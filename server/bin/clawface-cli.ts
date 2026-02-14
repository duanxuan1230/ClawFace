#!/usr/bin/env node
/**
 * Standalone CLI for testing ClawFace UDP communication.
 * Does NOT require OpenClaw — works independently.
 *
 * Direct mode (send to a specific target):
 *   npx tsx bin/clawface-cli.ts send-emotion JOY --host 192.168.1.100 --port 9527
 *   npx tsx bin/clawface-cli.ts send-mode THINKING --host 192.168.1.100
 *   npx tsx bin/clawface-cli.ts send-color "#FFDD33" --host 192.168.1.100
 *   npx tsx bin/clawface-cli.ts send-expression '{"mouthCurve":1.0}' --host 192.168.1.100
 *   npx tsx bin/clawface-cli.ts heartbeat --host 192.168.1.100 --count 5
 *   npx tsx bin/clawface-cli.ts demo --host 192.168.1.100
 *
 * Server mode (listen for client, then send):
 *   npx tsx bin/clawface-cli.ts serve --port 9527
 */

import { UdpSender } from '../src/udp-sender.js';
import { UdpServer } from '../src/udp-server.js';
import { EMOTIONS, FACE_MODES } from '../src/types.js';
import { heartbeatAckFrame } from '../src/frames.js';
import {
  emotionFrame, expressionFrame, modeFrame, colorFrame, heartbeatFrame,
} from '../src/frames.js';

const args = process.argv.slice(2);

function getFlag(name: string, fallback: string): string {
  const idx = args.indexOf(`--${name}`);
  if (idx !== -1 && args[idx + 1]) return args[idx + 1];
  return fallback;
}

const host = getFlag('host', '127.0.0.1');
const port = parseInt(getFlag('port', '9527'), 10);
const command = args[0];
const value = args[1];

let sender: UdpSender | null = null;

function getSender(): UdpSender {
  if (!sender) sender = new UdpSender(host, port);
  return sender;
}

async function run() {
  switch (command) {
    case 'send-emotion': {
      if (!value) { console.error(`Usage: send-emotion <${EMOTIONS.join('|')}>`); break; }
      const upper = value.toUpperCase();
      if (!EMOTIONS.includes(upper as any)) {
        console.error(`Unknown emotion: ${value}. Valid: ${EMOTIONS.join(', ')}`);
        break;
      }
      await getSender().send(emotionFrame(upper));
      console.log(`✓ Sent emotion: ${upper} → ${host}:${port}`);
      break;
    }

    case 'send-mode': {
      if (!value) { console.error(`Usage: send-mode <${FACE_MODES.join('|')}>`); break; }
      const upper = value.toUpperCase();
      if (!FACE_MODES.includes(upper as any)) {
        console.error(`Unknown mode: ${value}. Valid: ${FACE_MODES.join(', ')}`);
        break;
      }
      await getSender().send(modeFrame(upper));
      console.log(`✓ Sent mode: ${upper} → ${host}:${port}`);
      break;
    }

    case 'send-color': {
      if (!value) { console.error('Usage: send-color "#RRGGBB"'); break; }
      await getSender().send(colorFrame(value));
      console.log(`✓ Sent color: ${value} → ${host}:${port}`);
      break;
    }

    case 'send-expression': {
      if (!value) { console.error('Usage: send-expression \'{"mouthCurve":1.0}\''); break; }
      try {
        const params = JSON.parse(value);
        await getSender().send(expressionFrame(params));
        console.log(`✓ Sent expression → ${host}:${port}`);
      } catch {
        console.error('Invalid JSON');
      }
      break;
    }

    case 'heartbeat': {
      const count = parseInt(getFlag('count', '1'), 10);
      for (let i = 0; i < count; i++) {
        await getSender().send(heartbeatFrame());
        console.log(`✓ Heartbeat ${i + 1}/${count} → ${host}:${port}`);
        if (i < count - 1) await sleep(1000);
      }
      break;
    }

    case 'demo': {
      const interval = parseInt(getFlag('interval', '2000'), 10);
      console.log(`Demo: cycling through ${EMOTIONS.length} emotions (${interval}ms each) → ${host}:${port}`);
      for (const emotion of EMOTIONS) {
        await getSender().send(emotionFrame(emotion));
        console.log(`  → ${emotion}`);
        await sleep(interval);
      }
      console.log('Demo complete.');
      break;
    }

    case 'serve': {
      console.log(`[ClawFace CLI] Server mode — listening on UDP :${port}`);
      console.log('[ClawFace CLI] Waiting for Android client to connect...');
      console.log('[ClawFace CLI] Press Ctrl+C to stop.\n');

      const server = new UdpServer(port);

      server.onMessage = (msg, rinfo) => {
        console.log(`[${rinfo.address}:${rinfo.port}] ${msg}`);
        try {
          const frame = JSON.parse(msg);
          if (frame.type === 'heartbeat') {
            server.send(heartbeatAckFrame()).catch(() => {});
            console.log(`  → Sent heartbeat_ack to ${rinfo.address}:${rinfo.port}`);
          }
        } catch { /* ignore parse errors */ }
      };

      await server.start();

      // Interactive: read commands from stdin and send to connected client
      const readline = await import('node:readline');
      const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

      rl.on('line', async (line: string) => {
        const trimmed = line.trim();
        if (!trimmed) return;

        if (!server.hasClient()) {
          console.log('  ✗ No client connected yet.');
          return;
        }

        // Parse simple commands: emotion JOY, mode THINKING, color #FF0000
        const parts = trimmed.split(/\s+/);
        const cmd = parts[0].toLowerCase();
        const arg = parts.slice(1).join(' ');

        try {
          switch (cmd) {
            case 'emotion':
              await server.send(emotionFrame(arg.toUpperCase()));
              console.log(`  → Sent emotion: ${arg.toUpperCase()} to ${server.getClientInfo()}`);
              break;
            case 'mode':
              await server.send(modeFrame(arg.toUpperCase()));
              console.log(`  → Sent mode: ${arg.toUpperCase()} to ${server.getClientInfo()}`);
              break;
            case 'color':
              await server.send(colorFrame(arg));
              console.log(`  → Sent color: ${arg} to ${server.getClientInfo()}`);
              break;
            case 'expression':
              await server.send(expressionFrame(JSON.parse(arg)));
              console.log(`  → Sent expression to ${server.getClientInfo()}`);
              break;
            case 'demo':
              for (const emotion of EMOTIONS) {
                await server.send(emotionFrame(emotion));
                console.log(`  → ${emotion}`);
                await sleep(2000);
              }
              console.log('  Demo complete.');
              break;
            default:
              console.log('  Commands: emotion <name> | mode <name> | color <hex> | expression <json> | demo');
          }
        } catch (err) {
          console.error(`  ✗ Send failed: ${err}`);
        }
      });

      // Keep running until Ctrl+C
      await new Promise(() => {});
      break;
    }

    default:
      console.log(`ClawFace CLI — standalone UDP test tool

Commands (Direct mode):
  send-emotion <name>       Send emotion frame (${EMOTIONS.join(', ')})
  send-mode <name>          Send mode frame (${FACE_MODES.join(', ')})
  send-color <hex>          Send color frame (e.g., "#FFDD33")
  send-expression <json>    Send expression frame (JSON params)
  heartbeat                 Send heartbeat frame(s)
  demo                      Cycle through all 10 emotions

Commands (Server mode):
  serve                     Listen for client, interactive send

Options:
  --host <ip>     Target IP (default: 127.0.0.1)
  --port <num>    Target/listen port (default: 9527)
  --count <num>   Heartbeat count (default: 1)
  --interval <ms> Demo interval (default: 2000)
`);
  }

  sender?.destroy();
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

run().catch(console.error);
