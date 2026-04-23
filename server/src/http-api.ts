import type { IncomingMessage, ServerResponse } from 'node:http';
import type { WsServer } from './ws-server.js';
import { handleUpdateFace } from './tool-handler.js';

/**
 * HTTP API handler for the ClawFace daemon.
 *
 * POST /api/face   — update face (called by MCP tools)
 * GET  /api/status — connection status
 */
export function createHttpHandler(wsServer: WsServer) {
  return async (req: IncomingMessage, res: ServerResponse) => {
    res.setHeader('Content-Type', 'application/json');

    try {
      if (req.method === 'POST' && req.url === '/api/face') {
        const body = await readBody(req);
        const params = JSON.parse(body);
        const result = await handleUpdateFace(params, wsServer);
        res.writeHead(200);
        res.end(JSON.stringify({ ok: true, result }));
      } else if (req.method === 'GET' && req.url === '/api/status') {
        res.writeHead(200);
        res.end(JSON.stringify({
          connected: wsServer.hasClient(),
          client: wsServer.getClientInfo(),
        }));
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
