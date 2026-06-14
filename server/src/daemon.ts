#!/usr/bin/env node
/**
 * ClawFace Daemon — persistent process (managed by PM2).
 *
 * Single port, two protocols:
 *   ws://host:9527/ws    — Android client WebSocket
 *   http://host:9527/api — MCP tool HTTP calls
 *
 * Environment variables:
 *   CLAWFACE_PORT  (default: 9527)
 */

import { createServer } from 'node:http';
import { WebSocketServer } from 'ws';
import { WsServer } from './ws-server.js';
import { createHttpHandler } from './http-api.js';
import { QuotaPoller } from './quota-poller.js';

const port = parseInt(process.env.CLAWFACE_PORT ?? '9527', 10);

// --- WebSocket layer ---

const wsServer = new WsServer();
const wss = new WebSocketServer({ noServer: true });
wsServer.attach(wss);

// --- Quota poller（Claude 订阅配额定时抓取，默认关闭，由 App 开关控制） ---

const quotaPoller = new QuotaPoller();

// --- HTTP layer ---

const httpHandler = createHttpHandler(wsServer, quotaPoller);
const httpServer = createServer(httpHandler);

// WebSocket upgrade only on /ws path
httpServer.on('upgrade', (req, socket, head) => {
  if (req.url === '/ws') {
    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req);
    });
  } else {
    socket.destroy();
  }
});

httpServer.listen(port, () => {
  console.log(`[ClawFace] Daemon listening on :${port} (HTTP + WebSocket)`);
  console.log(`[ClawFace]   Android: ws://<host>:${port}/ws`);
  console.log(`[ClawFace]   API:     http://127.0.0.1:${port}/api/status`);
});

// --- Graceful shutdown ---

function shutdown() {
  console.log('[ClawFace] Shutting down...');
  quotaPoller.destroy();
  wsServer.destroy();
  wss.close();
  httpServer.close();
  process.exit(0);
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
