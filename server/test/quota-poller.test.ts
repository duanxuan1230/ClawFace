import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { normalizeUsage, QuotaPoller } from '../src/quota-poller.js';

const window = (windowMinutes: number, usedPercent = 42) => ({ windowMinutes, usedPercent, resetsAt: 1800000000 });
const family = (limitId: string, primary = window(300), secondary = window(10080)) => ({ limitId, primary, secondary });
const codex = (limits: unknown[], activeLimit: string | null = null) => ({ kind: 'codex_rate_limits', observedAt: 1790000000123, activeLimit, limits });

test('Codex selects active family, normalizes IDs, maps duration rather than position', () => {
  const result = normalizeUsage(codex([family('codex'), family('codex_other', window(10080, 71), window(300, 19))], 'codex-other'));
  assert.deepEqual(result, { data: { five_hour: { utilization: 19, resets_at: '2027-01-15T08:00:00.000Z' }, seven_day: { utilization: 71, resets_at: '2027-01-15T08:00:00.000Z' }, seven_day_opus: null, seven_day_sonnet: null }, fetchedAt: 1790000000123, error: null });
});

// Sanitized shape of the local HappyClaw usage snapshot: independent active
// header "premium", but windows only in the default x-codex-* family.
const defaultFamilySnapshot = {
  kind: 'codex_rate_limits', observedAt: 1790000000123,
  activeLimit: 'premium', rateLimitReachedType: null,
  limits: [{ limitId: 'codex', limitName: null,
    primary: { usedPercent: 11, windowMinutes: 300, resetsAt: 1800000000 },
    secondary: { usedPercent: 17, windowMinutes: 10080, resetsAt: 1800604800 },
    credits: null }],
};

test('Codex accepts the sole default family without treating premium as an alias', () => {
  const result = normalizeUsage(defaultFamilySnapshot);
  assert.equal(result.error, null);
  assert.equal(result.fetchedAt, defaultFamilySnapshot.observedAt);
  assert.equal(result.data?.five_hour?.utilization, 11);
  assert.equal(result.data?.seven_day?.utilization, 17);
  assert.equal(result.data?.five_hour?.resets_at, '2027-01-15T08:00:00.000Z');
  assert.equal(result.data?.seven_day?.resets_at, '2027-01-22T08:00:00.000Z');
  assert.equal(normalizeUsage({ ...defaultFamilySnapshot, activeLimit: 'unknown-tier' }).error, null);
  assert.throws(() => normalizeUsage(codex([family('codex'), family('other')], 'premium')), /activeLimit/);
  assert.throws(() => normalizeUsage(codex([family('premium_other')], 'premium')), /activeLimit/);
  assert.throws(() => normalizeUsage(codex([family('premium'), family('PREMIUM')], 'premium')), /activeLimit/);
});

test('Codex rejects ambiguous or missing active families and duplicate durations', () => {
  assert.throws(() => normalizeUsage(codex([family('a'), family('b')])), /歧义/);
  assert.throws(() => normalizeUsage(codex([family('a')], 'b')), /activeLimit/);
  assert.throws(() => normalizeUsage(codex([family('a', window(300), window(300))])), /重复/);
});

test('Codex empty snapshots and other durations never masquerade as known windows', () => {
  assert.equal(normalizeUsage(codex([])).data, null);
  assert.equal(normalizeUsage({ ...codex([family('a')]), observedAt: null }).fetchedAt, null);
  assert.equal(normalizeUsage(codex([family('a', window(60), window(1440))])).data, null);
});

test('Claude preserves numeric zero but never turns null utilization into zero', () => {
  const result = normalizeUsage({ data: { five_hour: { utilization: null }, seven_day: { utilization: 0, resets_at: null } }, fetchedAt: 12 });
  assert.equal(result.data?.five_hour, null);
  assert.equal(result.data?.seven_day?.utilization, 0);
  for (const status of ['unsupported', 'needs_relogin']) {
    assert.equal(normalizeUsage({ status, data: { seven_day: { utilization: 99 } } }).data, null);
    assert.match(normalizeUsage({ status }).error!, new RegExp(status));
  }
});

test('poller resolves system main each time, shares refresh, reuses cookie and clears stale data', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'quota-test-'));
  const savedEnv = { ...process.env };
  const savedFetch = globalThis.fetch;
  const requests: string[] = [];
  let id: string | null = 'connection-a';
  let forbidden = false;
  let expire = false;
  let logins = 0;
  try {
    writeFileSync(join(dir, 'password'), 'synthetic-test-password');
    process.env.QUOTA_CONFIG_FILE = join(dir, 'absent');
    process.env.QUOTA_STATE_FILE = join(dir, 'state');
    process.env.HC_USER = 'test-user';
    process.env.HC_PASS_FILE = join(dir, 'password');
    process.env.HC_BASE = 'http://test.invalid';
    globalThis.fetch = async (input, init) => {
      const path = new URL(String(input)).pathname;
      requests.push(path);
      if (path === '/api/auth/login') {
        logins++;
        return new Response('{}', { headers: { 'set-cookie': 'happyclaw_session=synthetic; Path=/' } });
      }
      assert.match(String((init?.headers as Record<string, string>).Cookie), /^happyclaw_session=/);
      if (expire) { expire = false; return new Response('{}', { status: 401 }); }
      if (forbidden) return new Response('{}', { status: 403 });
      if (path === '/api/agent-profiles') return Response.json({ inherited_model: { system_main: id ? { model_config_id: id } : null }, providers: [{ id: 'never-use-first' }] });
      return Response.json(id === 'connection-a' ? { data: { five_hour: { utilization: 10 } }, fetchedAt: 1 } : codex([]));
    };
    const poller = new QuotaPoller();
    const first = poller.pollOnce();
    assert.equal(poller.pollOnce(), first);
    await first;
    assert.equal(poller.getQuota().data?.five_hour?.utilization, 10);
    const restored = new QuotaPoller();
    assert.equal(restored.getQuota().data, null);
    restored.destroy();
    id = 'connection-b';
    await poller.pollOnce();
    assert.equal(poller.getQuota().data, null);
    assert.ok(requests.includes('/api/config/claude/providers/connection-b/usage'));
    assert.equal(logins, 1);
    expire = true;
    await poller.pollOnce();
    assert.equal(logins, 2);
    forbidden = true;
    await poller.pollOnce();
    assert.match(poller.getQuota().error!, /403/);
    assert.equal(logins, 2);
    assert.equal(poller.getQuota().data, null);
    forbidden = false;
    id = null;
    const count = requests.filter(p => p.endsWith('/usage')).length;
    await poller.pollOnce();
    assert.equal(requests.filter(p => p.endsWith('/usage')).length, count);
    assert.match(poller.getQuota().error!, /系统主连接/);
    poller.destroy();
  } finally {
    globalThis.fetch = savedFetch;
    process.env = savedEnv;
    rmSync(dir, { recursive: true, force: true });
  }
});
