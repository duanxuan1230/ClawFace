import dgram from 'node:dgram';

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

  /** Callback for incoming messages */
  onMessage: ((msg: string, rinfo: dgram.RemoteInfo) => void) | null = null;

  constructor(listenPort: number) {
    this.listenPort = listenPort;
  }

  /** Start listening. Resolves when the server is bound. */
  start(): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = dgram.createSocket('udp4');
      this.socket = socket;

      socket.on('message', (msg, rinfo) => {
        // Only remember non-loopback addresses as the real client
        const isLoopback = rinfo.address === '127.0.0.1' || rinfo.address === '::1';
        if (!isLoopback) {
          this.clientHost = rinfo.address;
          this.clientPort = rinfo.port;
        }
        this.onMessage?.(msg.toString('utf-8'), rinfo);
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

  /** Send data to the most recently seen client. Silently drops if no client. */
  async send(data: string): Promise<void> {
    if (!this.socket || !this.clientHost || !this.clientPort) return;
    return new Promise((resolve, reject) => {
      const buf = Buffer.from(data, 'utf-8');
      this.socket!.send(buf, 0, buf.length, this.clientPort!, this.clientHost!, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  /** Whether a client has been seen. */
  hasClient(): boolean {
    return this.clientHost !== null;
  }

  /** Get client info string for display. */
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
  }
}
