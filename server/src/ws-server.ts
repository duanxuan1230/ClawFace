import { WebSocketServer, WebSocket } from 'ws';
import type { Sender } from './types.js';

/**
 * WebSocket server for Android clients.
 *
 * Replaces UdpServer — same Sender interface, but over a persistent
 * WebSocket connection instead of UDP NAT hole-punching.
 *
 * Only one active client at a time (latest connection wins).
 * Server-side ping keeps the connection alive and detects dead clients.
 */
export class WsServer implements Sender {
  private client: WebSocket | null = null;
  private clientInfo = 'no client';
  private pingTimer: ReturnType<typeof setInterval> | null = null;

  private static PING_INTERVAL_MS = 30_000;

  /** Attach to a WebSocketServer and start managing connections. */
  attach(wss: WebSocketServer): void {
    wss.on('connection', (ws, req) => {
      const ip = req.headers['x-forwarded-for']
        ?? req.socket.remoteAddress
        ?? 'unknown';
      const port = req.socket.remotePort ?? 0;
      const info = `${ip}:${port}`;

      // Replace previous client
      if (this.client && this.client.readyState === WebSocket.OPEN) {
        console.log(`[WsServer] Replacing previous client ${this.clientInfo}`);
        this.client.close(1000, 'replaced');
      }

      this.client = ws;
      this.clientInfo = info;
      console.log(`[WsServer] Client connected: ${info}`);

      ws.on('close', () => {
        if (this.client === ws) {
          console.log(`[WsServer] Client disconnected: ${this.clientInfo}`);
          this.client = null;
          this.clientInfo = 'no client';
        }
      });

      ws.on('error', (err) => {
        console.error(`[WsServer] Client error: ${err.message}`);
      });

      // Handle application-level messages (e.g. heartbeat from older clients)
      ws.on('message', (data) => {
        try {
          const frame = JSON.parse(data.toString());
          if (frame.type === 'heartbeat') {
            ws.send(JSON.stringify({ type: 'heartbeat_ack' }));
          }
        } catch { /* ignore */ }
      });
    });

    // Periodic ping to detect dead connections
    this.pingTimer = setInterval(() => {
      if (this.client && this.client.readyState === WebSocket.OPEN) {
        this.client.ping();
      }
    }, WsServer.PING_INTERVAL_MS);
  }

  /** Send a JSON frame to the connected Android client. */
  async send(data: string): Promise<void> {
    if (!this.client || this.client.readyState !== WebSocket.OPEN) return;
    return new Promise((resolve, reject) => {
      this.client!.send(data, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  hasClient(): boolean {
    return this.client !== null && this.client.readyState === WebSocket.OPEN;
  }

  getClientInfo(): string {
    if (!this.hasClient()) return 'no client';
    return this.clientInfo;
  }

  destroy(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    if (this.client) {
      this.client.close(1000, 'shutdown');
      this.client = null;
    }
    this.clientInfo = 'no client';
  }
}
