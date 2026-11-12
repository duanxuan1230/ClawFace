/**
 * QuotaPoller — 定时从 HappyClaw 抓取系统主连接订阅配额。
 *
 * 设计要点：
 *  - 用普通 HTTP 登录 HappyClaw（admin 账号）：POST /api/auth/login 拿 happyclaw_session
 *    cookie，缓存复用（401 才重登），再调它自己的配额接口
 *    `/api/config/claude/providers/:id/usage`。全程不改 HappyClaw 源码。
 *  - 不再启动 headless 浏览器：登录是普通 JSON POST（无 CSRF / 浏览器挑战），
 *    省掉 Chromium 冷启动的数秒开销，手动刷新近乎秒回。
 *  - 默认 **关闭**。只有 App 里的开关打开（POST /api/quota/poller {enabled:true}）才会起定时器，
 *    关闭时零请求，避免无谓地反复打 Anthropic。
 *  - 凭据/身份不入库：账号、密码文件路径等从 `server/data/quota.config.json`（已 gitignore）
 *    或环境变量读取；代码里没有任何真实身份默认值。
 *  - 启停状态与快照持久化到 state 文件；重启只恢复开关，避免旧连接缓存串号。
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export interface QuotaBucket {
  utilization: number; // 0-100
  resets_at: string | null; // ISO 8601
}

export interface QuotaData {
  five_hour: QuotaBucket | null;
  seven_day: QuotaBucket | null;
  seven_day_opus: QuotaBucket | null;
  seven_day_sonnet: QuotaBucket | null;
}

export interface QuotaSnapshot {
  data: QuotaData | null;
  fetchedAt: number | null; // epoch ms
  error: string | null;
}

export interface PollerStatus {
  enabled: boolean;
  intervalMs: number;
  polling: boolean;
  lastFetchedAt: number | null;
  lastError: string | null;
  hasData: boolean;
  configured: boolean;
}

interface PollerConfig {
  hcBase: string; // HappyClaw 地址
  hcUser: string; // admin 用户名（不入库）
  hcPassFile: string; // 密码文件路径（0600）
  intervalMs: number; // 轮询间隔
  stateFile: string; // 启停状态 + 快照持久化路径
}

type JsonObject = Record<string, unknown>;
const object = (v: unknown): JsonObject =>
  v !== null && typeof v === 'object' && !Array.isArray(v) ? v as JsonObject : {};
const finite = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
const emptyData = (): QuotaData => ({
  five_hour: null, seven_day: null, seven_day_opus: null, seven_day_sonnet: null,
});

/** Adapt HappyClaw usage without changing the Android HTTP contract. */
export function normalizeUsage(value: unknown): QuotaSnapshot {
  const usage = object(value);
  if (usage.status === 'unsupported' || usage.status === 'needs_relogin') {
    return { data: null, fetchedAt: null, error: `HappyClaw usage: ${usage.status}` };
  }
  if (usage.kind === 'codex_rate_limits') {
    const limits = Array.isArray(usage.limits) ? usage.limits.map(object) : [];
    if (!limits.length || !finite(usage.observedAt)) {
      return { data: null, fetchedAt: null, error: 'Codex 尚无用量快照' };
    }
    const normalizeId = (id: string) => id.trim().toLowerCase().replace(/-/g, '_');
    const active = typeof usage.activeLimit === 'string' && usage.activeLimit.trim()
      ? normalizeId(usage.activeLimit) : null;
    const matches = active
      ? limits.filter((l: JsonObject) => typeof l.limitId === 'string' && normalizeId(l.limitId) === active)
      : limits;
    // HappyClaw stores x-codex-active-limit independently from window headers.
    // An observed activeLimit of "premium" does not name the default "codex"
    // family parsed from x-codex-{primary,secondary}-*. It is NOT an alias.
    // Without an exact match, accept only the sole, protocol-default codex
    // family; never guess between multiple families or choose an arbitrary ID.
    const soleDefault = limits.length === 1 && typeof limits[0].limitId === 'string'
      && normalizeId(limits[0].limitId) === 'codex';
    const candidates = active && matches.length === 0 && soleDefault ? limits : matches;
    if (candidates.length !== 1) throw new Error(active
      ? 'Codex activeLimit 未唯一匹配限额族' : 'Codex 多限额族歧义，缺少 activeLimit');
    const data = emptyData();
    for (const raw of [candidates[0].primary, candidates[0].secondary]) {
      const window = object(raw);
      const key = window.windowMinutes === 300 ? 'five_hour'
        : window.windowMinutes === 10080 ? 'seven_day' : null;
      if (!key || !finite(window.usedPercent)) continue;
      if (data[key]) throw new Error('Codex 同时存在重复时长窗口');
      const reset = finite(window.resetsAt) ? new Date(window.resetsAt * 1000) : null;
      data[key] = { utilization: window.usedPercent,
        resets_at: reset && finite(reset.getTime()) ? reset.toISOString() : null };
    }
    return { data: data.five_hour || data.seven_day ? data : null,
      fetchedAt: usage.observedAt,
      error: data.five_hour || data.seven_day ? null : 'Codex 没有 300/10080 分钟用量窗口' };
  }
  if (usage.kind != null) throw new Error('未知 HappyClaw usage 格式');
  const data = emptyData();
  const source = object(usage.data);
  for (const key of Object.keys(data) as Array<keyof QuotaData>) {
    const bucket = object(source[key]);
    // Android expects a number: unknown utilization is an absent bucket, never 0.
    if (finite(bucket.utilization)) data[key] = {
      utilization: bucket.utilization,
      resets_at: typeof bucket.resets_at === 'string' ? bucket.resets_at : null,
    };
  }
  return { data: Object.values(data).some(Boolean) ? data : null,
    fetchedAt: finite(usage.fetchedAt) ? usage.fetchedAt : null,
    error: typeof usage.error === 'string' ? usage.error : null };
}

const DEFAULT_DATA_DIR = fileURLToPath(new URL('../data/', import.meta.url));

function loadPollerConfig(): PollerConfig {
  // 本地 gitignore 配置文件优先，环境变量次之，最后是安全默认值（不含真实身份）
  let fileCfg: Partial<PollerConfig> = {};
  const cfgPath =
    process.env.QUOTA_CONFIG_FILE ?? `${DEFAULT_DATA_DIR}quota.config.json`;
  try {
    if (existsSync(cfgPath)) {
      fileCfg = JSON.parse(readFileSync(cfgPath, 'utf8')) as Partial<PollerConfig>;
    }
  } catch (e) {
    console.error('[quota] 读取配置文件失败:', e instanceof Error ? e.message : e);
  }

  const intervalRaw =
    process.env.QUOTA_POLL_INTERVAL_MS ??
    (fileCfg.intervalMs != null ? String(fileCfg.intervalMs) : '600000');

  return {
    hcBase: process.env.HC_BASE ?? fileCfg.hcBase ?? 'http://127.0.0.1:3001',
    hcUser: process.env.HC_USER ?? fileCfg.hcUser ?? '',
    hcPassFile: process.env.HC_PASS_FILE ?? fileCfg.hcPassFile ?? '',
    intervalMs: Math.max(60_000, parseInt(intervalRaw, 10) || 600_000),
    stateFile:
      process.env.QUOTA_STATE_FILE ??
      fileCfg.stateFile ??
      `${DEFAULT_DATA_DIR}quota-state.json`,
  };
}

export class QuotaPoller {
  private cfg: PollerConfig;
  private enabled = false;
  private timer: ReturnType<typeof setInterval> | null = null;
  private polling = false;
  private inFlight: Promise<void> | null = null;
  private snapshot: QuotaSnapshot = { data: null, fetchedAt: null, error: null };
  private sessionCookie: string | null = null; // 缓存的 happyclaw_session，401 才重登

  constructor() {
    this.cfg = loadPollerConfig();
    this.restoreState();
    if (this.enabled) this.start();
    console.log(
      `[quota] poller 初始化: enabled=${this.enabled} interval=${this.cfg.intervalMs / 1000}s configured=${this.isConfigured()}`,
    );
  }

  private isConfigured(): boolean {
    return Boolean(this.cfg.hcUser && this.cfg.hcPassFile);
  }

  private restoreState(): void {
    try {
      if (existsSync(this.cfg.stateFile)) {
        const s = JSON.parse(readFileSync(this.cfg.stateFile, 'utf8'));
        this.enabled = Boolean(s.enabled);
        // A persisted snapshot cannot prove which connection is system main now.
        // Restore the switch only; refresh before publishing any quota.
      }
    } catch (e) {
      console.error('[quota] 恢复状态失败:', e instanceof Error ? e.message : e);
    }
  }

  private persistState(): void {
    try {
      mkdirSync(dirname(this.cfg.stateFile), { recursive: true });
      writeFileSync(
        this.cfg.stateFile,
        JSON.stringify({ enabled: this.enabled, snapshot: this.snapshot }, null, 2),
      );
    } catch (e) {
      console.error('[quota] 持久化状态失败:', e instanceof Error ? e.message : e);
    }
  }

  /** App 开关键调用：打开/关闭定时轮询 */
  setEnabled(on: boolean): PollerStatus {
    if (on !== this.enabled) {
      this.enabled = on;
      if (on) this.start();
      else this.stop();
      this.persistState();
    }
    return this.getStatus();
  }

  private start(): void {
    if (this.timer) return;
    void this.pollOnce(); // 打开即刻拉一次
    this.timer = setInterval(() => void this.pollOnce(), this.cfg.intervalMs);
    console.log(`[quota] 轮询已开启，每 ${this.cfg.intervalMs / 1000}s 一次`);
  }

  private stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    console.log('[quota] 轮询已关闭');
  }

  /** 登录 HappyClaw，提取并缓存 happyclaw_session cookie */
  private async login(): Promise<void> {
    const pass = readFileSync(this.cfg.hcPassFile, 'utf8').trim();
    const resp = await fetch(`${this.cfg.hcBase}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: this.cfg.hcUser, password: pass }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!resp.ok) {
      throw new Error(`登录失败：HTTP ${resp.status}（密码或账号有误？）`);
    }
    // Node fetch 用 getSetCookie() 取多条 Set-Cookie，回退到合并头
    const setCookies = resp.headers.getSetCookie?.() ?? [];
    const raw = setCookies.length
      ? setCookies.join('; ')
      : (resp.headers.get('set-cookie') ?? '');
    const m = raw.match(/happyclaw_session=([^;]+)/);
    if (!m) throw new Error('登录失败：未拿到 happyclaw_session');
    this.sessionCookie = `happyclaw_session=${m[1]}`;
  }

  /** 带缓存 cookie 的 GET；遇 401 自动重登一次再试 */
  private async hcGet(path: string): Promise<Response> {
    if (!this.sessionCookie) await this.login();
    const doFetch = () =>
      fetch(`${this.cfg.hcBase}${path}`, {
        headers: { Cookie: this.sessionCookie! },
        signal: AbortSignal.timeout(15_000),
      });
    let resp = await doFetch();
    if (resp.status === 401) {
      this.sessionCookie = null;
      await this.login();
      resp = await doFetch();
    }
    return resp;
  }

  /** 执行一次抓取（带并发自锁，不会重入） */
  pollOnce(): Promise<void> {
    if (this.inFlight) return this.inFlight;
    this.inFlight = this.fetchOnce().finally(() => { this.inFlight = null; });
    return this.inFlight;
  }

  private async fetchOnce(): Promise<void> {
    if (!this.isConfigured()) {
      this.snapshot = {
        data: null, fetchedAt: null,
        error: '未配置 hcUser / hcPassFile（见 server/data/quota.config.json）',
      };
      this.persistState();
      return;
    }
    this.polling = true;
    this.snapshot = { data: null, fetchedAt: null, error: null };
    try {
      // Resolve the authoritative system-main binding on every refresh.
      const provResp = await this.hcGet('/api/agent-profiles');
      if (!provResp.ok) throw new Error(`agent-profiles 接口 HTTP ${provResp.status}`);
      const profiles = await provResp.json();
      const id = profiles?.inherited_model?.system_main?.model_config_id;
      if (typeof id !== 'string' || !id.trim()) {
        throw new Error('HappyClaw 系统主连接未配置或不可用');
      }
      const usageResp = await this.hcGet(
        `/api/config/claude/providers/${encodeURIComponent(id)}/usage`,
      );
      if (!usageResp.ok) throw new Error(`usage 接口 HTTP ${usageResp.status}`);
      this.snapshot = normalizeUsage(await usageResp.json());
      this.persistState();
      console.log('[quota] 抓取成功:', JSON.stringify(this.snapshot.data));
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      this.snapshot = { ...this.snapshot, error: msg };
      this.persistState();
      console.error('[quota] 抓取失败:', msg);
    } finally {
      this.polling = false;
    }
  }

  getQuota(): QuotaSnapshot {
    return this.snapshot;
  }

  getStatus(): PollerStatus {
    return {
      enabled: this.enabled,
      intervalMs: this.cfg.intervalMs,
      polling: this.polling,
      lastFetchedAt: this.snapshot.fetchedAt,
      lastError: this.snapshot.error,
      hasData: Boolean(this.snapshot.data),
      configured: this.isConfigured(),
    };
  }

  destroy(): void {
    this.stop();
  }
}
