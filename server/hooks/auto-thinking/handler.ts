import dgram from 'node:dgram';
import fs from 'node:fs';
import path from 'node:path';

/** Same path used by UdpServer to persist client address */
const CLIENT_FILE = path.join(
  process.env.HOME || process.env.USERPROFILE || '/tmp',
  '.clawface-client.json',
);

const THINKING_FRAME = JSON.stringify({ type: 'mode', mode: 'THINKING' });

const handler = async (event: any) => {
  if (event.type !== 'agent' || event.action !== 'bootstrap') return;

  let clientInfo: { host: string; port: number; listenPort: number };
  try {
    const raw = fs.readFileSync(CLIENT_FILE, 'utf-8');
    clientInfo = JSON.parse(raw);
  } catch {
    // No client file — plugin hasn't seen a client yet, nothing to do
    return;
  }

  if (!clientInfo.host || !clientInfo.port) return;

  // Send THINKING frame via a temporary UDP socket bound to the plugin's listen port
  // so the packet appears to come from the same source the client knows
  return new Promise<void>((resolve) => {
    const socket = dgram.createSocket('udp4');
    const buf = Buffer.from(THINKING_FRAME, 'utf-8');

    // Bind to the same port the server listens on, using SO_REUSEADDR
    socket.bind({ port: clientInfo.listenPort, exclusive: false }, () => {
      socket.send(buf, 0, buf.length, clientInfo.port, clientInfo.host, () => {
        socket.close();
        resolve();
      });
    });

    // If bind fails (port busy without reuse support), try sending without bind
    socket.on('error', () => {
      const fallback = dgram.createSocket('udp4');
      fallback.send(buf, 0, buf.length, clientInfo.port, clientInfo.host, () => {
        fallback.close();
        resolve();
      });
    });
  });
};

export default handler;
