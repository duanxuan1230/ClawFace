import dgram from 'node:dgram';
import fs from 'node:fs';
import path from 'node:path';

const CLIENT_FILE = path.join(
  process.env.HOME || process.env.USERPROFILE || '/tmp',
  '.clawface-client.json',
);

// 内部命令帧，发给 localhost 让 UdpServer 处理
const THINKING_CMD = JSON.stringify({ type: 'hook_cmd', cmd: 'thinking' });

const handler = async (event: any) => {
  console.log('[auto-thinking] hook fired, event:', JSON.stringify(event).slice(0, 200));

  // 宽松匹配：不再严格校验 event.type/action，只要 hook 被调用就执行
  let listenPort = 9527; // 默认端口
  try {
    const raw = fs.readFileSync(CLIENT_FILE, 'utf-8');
    const info = JSON.parse(raw);
    if (info.listenPort) listenPort = info.listenPort;
    console.log('[auto-thinking] client file found, listenPort:', listenPort);
  } catch (err) {
    console.log('[auto-thinking] no client file, using default port');
  }

  // 发送内部命令到 localhost，由 UdpServer 转发
  return new Promise<void>((resolve) => {
    const sock = dgram.createSocket('udp4');
    const buf = Buffer.from(THINKING_CMD, 'utf-8');
    sock.send(buf, 0, buf.length, listenPort, '127.0.0.1', (err) => {
      if (err) console.error('[auto-thinking] send error:', err.message);
      else console.log('[auto-thinking] loopback cmd sent to localhost:', listenPort);
      sock.close();
      resolve();
    });
  });
};

export default handler;
