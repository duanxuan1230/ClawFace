#!/usr/bin/env node
/**
 * Standalone CLI for testing ClawFace.
 *
 * Sends commands to the running ClawFace daemon via HTTP API:
 *   npx tsx bin/clawface-cli.ts send-emotion JOY
 *   npx tsx bin/clawface-cli.ts send-mode THINKING
 *   npx tsx bin/clawface-cli.ts send-color "#FFDD33"
 *   npx tsx bin/clawface-cli.ts send-expression '{"mouthCurve":1.0}'
 *   npx tsx bin/clawface-cli.ts demo
 *   npx tsx bin/clawface-cli.ts status
 *
 * Options:
 *   --host <ip>     Daemon host (default: 127.0.0.1)
 *   --port <num>    Daemon port (default: 9527)
 */

import { EMOTIONS, FACE_MODES } from '../src/types.js';

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

const API_BASE = `http://${host}:${port}`;

async function updateFace(params: Record<string, unknown>): Promise<string> {
  const res = await fetch(`${API_BASE}/api/face`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  const data = await res.json() as { ok: boolean; result: string; error?: string };
  if (!data.ok) throw new Error(data.error ?? 'Unknown error');
  return data.result;
}

async function getStatus(): Promise<{ connected: boolean; client: string }> {
  const res = await fetch(`${API_BASE}/api/status`);
  return await res.json() as { connected: boolean; client: string };
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
      const result = await updateFace({ emotion: upper });
      console.log(`✓ ${result}`);
      break;
    }

    case 'send-mode': {
      if (!value) { console.error(`Usage: send-mode <${FACE_MODES.join('|')}>`); break; }
      const upper = value.toUpperCase();
      if (!FACE_MODES.includes(upper as any)) {
        console.error(`Unknown mode: ${value}. Valid: ${FACE_MODES.join(', ')}`);
        break;
      }
      const result = await updateFace({ mode: upper });
      console.log(`✓ ${result}`);
      break;
    }

    case 'send-color': {
      if (!value) { console.error('Usage: send-color "#RRGGBB"'); break; }
      const result = await updateFace({ color: value });
      console.log(`✓ ${result}`);
      break;
    }

    case 'send-expression': {
      if (!value) { console.error('Usage: send-expression \'{"mouthCurve":1.0}\''); break; }
      try {
        const params = JSON.parse(value);
        const result = await updateFace({ expression: params });
        console.log(`✓ ${result}`);
      } catch {
        console.error('Invalid JSON');
      }
      break;
    }

    case 'demo': {
      const interval = parseInt(getFlag('interval', '2000'), 10);
      console.log(`Demo: cycling through ${EMOTIONS.length} emotions (${interval}ms each)`);
      for (const emotion of EMOTIONS) {
        const result = await updateFace({ emotion });
        console.log(`  → ${emotion}: ${result}`);
        await sleep(interval);
      }
      console.log('Demo complete.');
      break;
    }

    case 'status': {
      const status = await getStatus();
      console.log(`Connected: ${status.connected}`);
      console.log(`Client:    ${status.client}`);
      break;
    }

    default:
      console.log(`ClawFace CLI — sends commands to the running daemon

Commands:
  send-emotion <name>       Send emotion (${EMOTIONS.join(', ')})
  send-mode <name>          Send mode (${FACE_MODES.join(', ')})
  send-color <hex>          Send color (e.g., "#FFDD33")
  send-expression <json>    Send expression (JSON params)
  demo                      Cycle through all 10 emotions
  status                    Check daemon & client status

Options:
  --host <ip>     Daemon host (default: 127.0.0.1)
  --port <num>    Daemon port (default: 9527)
  --interval <ms> Demo interval (default: 2000)
`);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

run().catch((err) => {
  if (err.cause?.code === 'ECONNREFUSED') {
    console.error(`✗ Cannot connect to daemon at ${API_BASE}. Is it running?`);
    console.error('  Start it with: npm start  (or: pm2 start dist/daemon.js --name clawface)');
  } else {
    console.error(`✗ ${err.message}`);
  }
  process.exit(1);
});
