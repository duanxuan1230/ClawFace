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

test('AGY quotas normalize all groups and pick Gemini Models as primary data', () => {
  const result = normalizeUsage({
    kind: 'agy_quotas',
    fetchedAt: '2026-11-21T03:00:00.000Z',
    groups: [
      {
        group: 'Claude',
        fiveHour: { label: '5h', windowKind: 'five_hour', usedPercent: 12, remainingPercent: 88, resetsAt: '2026-11-21T08:00:00.000Z' },
        weekly: { label: 'Weekly', windowKind: 'weekly', usedPercent: 30, remainingPercent: 70, resetsAt: null },
        others: [],
      },
      {
        group: 'Gemini Models',
        fiveHour: { label: '5h', windowKind: 'five_hour', usedPercent: 25, remainingPercent: 75, resetsAt: '2026-11-21T07:00:00.000Z' },
        weekly: { label: 'Weekly', windowKind: 'weekly', usedPercent: 40, remainingPercent: 60, resetsAt: '2026-11-28T00:00:00.000Z' },
        others: [],
      },
    ],
  });
  assert.equal(result.error, null);
  assert.equal(result.groups?.length, 2);
  assert.equal(result.data?.five_hour?.utilization, 25);
  assert.equal(result.data?.seven_day?.utilization, 40);
  assert.equal(result.fetchedAt, Date.parse('2026-11-21T03:00:00.000Z'));
});

test('poller supports listing providers, selecting provider, and reporting unsupported usage', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'quota-provider-test-'));
  const savedEnv = { ...process.env };
  const savedFetch = globalThis.fetch;
  try {
    writeFileSync(join(dir, 'password'), 'synthetic-test-password');
    process.env.QUOTA_CONFIG_FILE = join(dir, 'absent');
    process.env.QUOTA_STATE_FILE = join(dir, 'state.json');
    process.env.HC_USER = 'test-user';
    process.env.HC_PASS_FILE = join(dir, 'password');
    process.env.HC_BASE = 'http://test.invalid';
    globalThis.fetch = async (input) => {
      const path = new URL(String(input)).pathname;
      if (path === '/api/auth/login') {
        return new Response('{}', { headers: { 'set-cookie': 'happyclaw_session=synthetic; Path=/' } });
      }
      if (path === '/api/agent-profiles') {
        return Response.json({ inherited_model: { system_main: { model_config_id: 'p-claude' } } });
      }
      if (path === '/api/config/claude/providers') {
        return Response.json({
          providers: [
            { id: 'p-claude', name: 'Claude Main', runner: 'claude' },
            { id: 'p-agy', name: 'Gemini AGY', runner: 'agy' },
            { id: 'p-apikey', name: 'ThirdParty Key', runner: 'claude' },
          ],
        });
      }
      if (path === '/api/config/claude/providers/p-agy/usage') {
        return Response.json({
          kind: 'agy_quotas',
          fetchedAt: '2026-11-21T03:00:00.000Z',
          groups: [
            { group: 'Gemini Models', fiveHour: { usedPercent: 18, resetsAt: null }, weekly: { usedPercent: 33, resetsAt: null } },
          ],
        });
      }
      if (path === '/api/config/claude/providers/p-apikey/usage') {
        return Response.json({ error: 'No OAuth' }, { status: 400 });
      }
      return Response.json({ data: { five_hour: { utilization: 5 } }, fetchedAt: 100 });
    };
    const poller = new QuotaPoller();
    const list = await poller.getProviders();
    assert.equal(list.providers.length, 3);
    assert.equal(list.providers[0].isSystemMain, true);
    assert.equal(list.selectedProviderId, null);

    const agySnap = await poller.selectProvider('p-agy');
    assert.equal(agySnap.providerId, 'p-agy');
    assert.equal(agySnap.providerName, 'Gemini AGY');
    assert.equal(agySnap.data?.five_hour?.utilization, 18);

    const restored = new QuotaPoller();
    await restored.pollOnce();
    assert.equal(restored.getQuota().providerId, 'p-agy');
    restored.destroy();

    const unsupportedSnap = await poller.selectProvider('p-apikey');
    assert.equal(unsupportedSnap.error, '不支持用量查询');
    assert.equal(unsupportedSnap.providerName, 'ThirdParty Key');
    poller.destroy();
  } finally {
    globalThis.fetch = savedFetch;
    process.env = savedEnv;
    rmSync(dir, { recursive: true, force: true });
  }
});
