import dgram from 'node:dgram';
import fs from 'node:fs';
import path from 'node:path';

/** Default path for the client address file */
const DEFAULT_CLIENT_FILE = path.join(
  process.env.HOME || process.env.USERPROFILE || '/tmp',
  '.clawface-client.json',
);

/**
 * Bidirectional UDP server.
 *
 * Listens on a local port. When a client (Android) sends any packet
 * (e.g., heartbeat), we remember its address (NAT hole-punching).
 * All subsequent send() calls go to that remembered address.
 *
 * This enables the "reverse direction" pattern:
 *   Phone --heartbeat--> VPS (learns phone's address)
 *   VPS --emotion/expression frames--> Phone (via remembered address)
 */
export class UdpServer {
  private socket: dgram.Socket | null = null;
  private listenPort: number;

  /** The most recent client address (set when we receive any packet) */
  private clientHost: string | null = null;
  private clientPort: number | null = null;

  /** Path to write client address for cross-process sharing (hooks) */
  private clientFilePath: string;

  /** Callback for incoming messages */
  onMessage: ((msg: string, rinfo: dgram.RemoteInfo) => void) | null = null;

  constructor(listenPort: number, clientFilePath?: string) {
    this.listenPort = listenPort;
    this.clientFilePath = clientFilePath ?? DEFAULT_CLIENT_FILE;
  }

  /**
   * Start listening on the configured port.
   * Returns a promise that resolves when the server is bound.
   */
  start(): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = dgram.createSocket('udp4');
      this.socket = socket;

      socket.on('message', (msg, rinfo) => {
        // Remember client address for reverse communication
        this.clientHost = rinfo.address;
        this.clientPort = rinfo.port;

        // Persist client address to file so hooks can read it
        this.writeClientFile();

        const message = msg.toString('utf-8');
        this.onMessage?.(message, rinfo);
      });

      socket.on('error', (err) => {
        console.error(`[UdpServer] Error: ${err.message}`);
        socket.close();
        this.socket = null;
        reject(err);
      });

      socket.bind(this.listenPort, () => {
        console.log(`[UdpServer] Listening on UDP port ${this.listenPort}`);
        resolve();
      });
    });
  }

  /**
   * Send data to the most recently seen client.
   * If no client has connected yet, the send is silently dropped.
   */
  async send(data: string): Promise<void> {
    if (!this.socket || !this.clientHost || !this.clientPort) {
      return; // No client connected yet, silently drop
    }
    return new Promise((resolve, reject) => {
      const buf = Buffer.from(data, 'utf-8');
      this.socket!.send(buf, 0, buf.length, this.clientPort!, this.clientHost!, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  /**
   * Send data to a specific host:port (for direct mode / CLI testing).
   */
  async sendTo(data: string, host: string, port: number): Promise<void> {
    const socket = this.socket ?? dgram.createSocket('udp4');
    if (!this.socket) this.socket = socket;
    return new Promise((resolve, reject) => {
      const buf = Buffer.from(data, 'utf-8');
      socket.send(buf, 0, buf.length, port, host, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  /** Whether a client has been seen */
  hasClient(): boolean {
    return this.clientHost !== null;
  }

  /** Get client info string */
  getClientInfo(): string {
    if (!this.clientHost) return 'no client';
    return `${this.clientHost}:${this.clientPort}`;
  }

  destroy(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.clientHost = null;
    this.clientPort = null;
    this.removeClientFile();
  }

  /** Write current client address to a JSON file for cross-process sharing */
  private writeClientFile(): void {
    try {
      const data = JSON.stringify({
        host: this.clientHost,
        port: this.clientPort,
        listenPort: this.listenPort,
        ts: Date.now(),
      });
      fs.writeFileSync(this.clientFilePath, data, 'utf-8');
    } catch { /* best-effort */ }
  }

  /** Remove client file on shutdown */
  private removeClientFile(): void {
    try { fs.unlinkSync(this.clientFilePath); } catch { /* ignore */ }
  }

  /** Get the client file path (for hooks to read) */
  static getClientFilePath(): string {
    return DEFAULT_CLIENT_FILE;
  }
}
