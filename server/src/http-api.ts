import { timingSafeEqual } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { WsServer } from './ws-server.js';
import type { QuotaPoller } from './quota-poller.js';
import { handleUpdateFace } from './tool-handler.js';

const DEFAULT_TOKEN_FILE = fileURLToPath(new URL('../data/auth.token', import.meta.url));

export function loadAuthToken(): string | null {
  const envTok = process.env.CLAWFACE_TOKEN?.trim();
  if (envTok) return envTok;
  try {
    if (existsSync(DEFAULT_TOKEN_FILE)) {
      const fileTok = readFileSync(DEFAULT_TOKEN_FILE, 'utf8').trim();
      if (fileTok) return fileTok;
    }
  } catch {
    // ignore
  }
  return null;
}

function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}

export function isAuthorized(req: IncomingMessage, token: string | null): boolean {
  if (!token) return true;
  const fwd = req.headers['x-forwarded-for'];
  const addr = req.socket.remoteAddress ?? '';
  const isLoopback =
    !fwd && (addr === '127.0.0.1' || addr === '::1' || addr === '::ffff:127.0.0.1');
  if (isLoopback) return true;

  const auth = req.headers.authorization;
  if (typeof auth === 'string' && auth.startsWith('Bearer ')) {
    if (safeEqual(auth.slice(7).trim(), token)) return true;
  }
  const qIdx = (req.url ?? '').indexOf('?');
  if (qIdx >= 0) {
    const params = new URLSearchParams((req.url ?? '').slice(qIdx + 1));
    const qTok = params.get('token');
    if (qTok && safeEqual(qTok, token)) return true;
  }
  return false;
}

/**
 * HTTP API handler for the ClawFace daemon.
 *
 * POST /api/face          — update face (called by MCP tools)
 * GET  /api/status        — connection status
 * GET  /api/quota         — 最近一次抓到的 Claude 订阅配额快照（给 Widget 取）
 * GET  /api/quota/poller  — 查看配额轮询开关状态
 * POST /api/quota/poller  — App 开关键：{ "enabled": true|false } 控制轮询启停
 * POST /api/quota/refresh — 手动刷新：立即强制抓一次（不论开关开没开），返回最新快照
 */
export function createHttpHandler(
  wsServer: WsServer,
  quotaPoller: QuotaPoller,
  authToken: string | null = null,
) {
  return async (req: IncomingMessage, res: ServerResponse) => {
    res.setHeader('Content-Type', 'application/json');

    if (!isAuthorized(req, authToken)) {
      res.writeHead(401);
      res.end(JSON.stringify({ error: 'Unauthorized' }));
      return;
    }

    // 去掉查询串，只按路径匹配
    const path = (req.url ?? '').split('?')[0];

    try {
      if (req.method === 'POST' && path === '/api/face') {
        const body = await readBody(req);
        const params = JSON.parse(body);
        const result = await handleUpdateFace(params, wsServer);
        res.writeHead(200);
        res.end(JSON.stringify({ ok: true, result }));
      } else if (req.method === 'GET' && path === '/api/status') {
        res.writeHead(200);
        res.end(JSON.stringify({
          connected: wsServer.hasClient(),
          client: wsServer.getClientInfo(),
        }));
      } else if (req.method === 'GET' && path === '/api/quota') {
        res.writeHead(200);
        res.end(JSON.stringify(quotaPoller.getQuota()));
      } else if (req.method === 'GET' && path === '/api/quota/poller') {
        res.writeHead(200);
        res.end(JSON.stringify(quotaPoller.getStatus()));
      } else if (req.method === 'POST' && path === '/api/quota/poller') {
        const body = await readBody(req);
        const parsed = body ? JSON.parse(body) : {};
        if (typeof parsed.enabled !== 'boolean') {
          res.writeHead(400);
          res.end(JSON.stringify({ error: 'body 需要 { "enabled": true|false }' }));
          return;
        }
        const status = quotaPoller.setEnabled(parsed.enabled);
        res.writeHead(200);
        res.end(JSON.stringify({ ok: true, status }));
      } else if (req.method === 'POST' && path === '/api/quota/refresh') {
        // 手动强制抓一次（不论开关状态），完成后返回最新快照
        await quotaPoller.pollOnce();
        res.writeHead(200);
        res.end(JSON.stringify(quotaPoller.getQuota()));
      } else if (req.method === 'GET' && path === '/api/quota/providers') {
        const result = await quotaPoller.getProviders();
        res.writeHead(200);
        res.end(JSON.stringify(result));
      } else if (req.method === 'POST' && path === '/api/quota/provider') {
        const body = await readBody(req);
        const parsed = body ? JSON.parse(body) : {};
        const providerId =
          parsed.providerId === null || typeof parsed.providerId === 'string'
            ? parsed.providerId
            : null;
        const snap = await quotaPoller.selectProvider(providerId);
        res.writeHead(200);
        res.end(JSON.stringify(snap));
      } else {
        res.writeHead(404);
        res.end(JSON.stringify({ error: 'Not Found' }));
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      res.writeHead(500);
      res.end(JSON.stringify({ error: message }));
    }
  };
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
    req.on('error', reject);
  });
}
