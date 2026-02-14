import dgram from 'node:dgram';

/**
 * UDP sender using Node.js dgram. Lazily creates a socket, reuses across sends.
 */
export class UdpSender {
  private socket: dgram.Socket | null = null;
  private host: string;
  private port: number;

  constructor(host: string, port: number) {
    this.host = host;
    this.port = port;
  }

  private ensureSocket(): dgram.Socket {
    if (!this.socket) {
      this.socket = dgram.createSocket('udp4');
    }
    return this.socket;
  }

  async send(data: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = this.ensureSocket();
      const buf = Buffer.from(data, 'utf-8');
      socket.send(buf, 0, buf.length, this.port, this.host, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  updateTarget(host: string, port: number): void {
    this.host = host;
    this.port = port;
  }

  destroy(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }
}
