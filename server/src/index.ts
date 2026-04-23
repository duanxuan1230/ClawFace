#!/usr/bin/env node
/**
 * ClawFace MCP Server
 *
 * Lightweight MCP proxy — all face control and status queries are forwarded
 * to the persistent ClawFace daemon via HTTP.
 *
 * The daemon must be running separately (e.g., via PM2):
 *   pm2 start dist/daemon.js --name clawface
 *
 * Configuration via environment variables:
 *   CLAWFACE_PORT=9527  (must match the daemon's port)
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { UpdateFaceSchema } from './schemas.js';

const DAEMON_PORT = parseInt(process.env.CLAWFACE_PORT ?? '9527', 10);
const DAEMON_URL = `http://127.0.0.1:${DAEMON_PORT}`;

console.error(`[ClawFace MCP] Forwarding to daemon at ${DAEMON_URL}`);

// --- MCP Server ---

const server = new McpServer({
  name: 'clawface',
  version: '2.0.0',
});

// Tool: update_face (proxy to daemon HTTP API)
server.tool(
  'update_face',
  `Control the ClawFace virtual face on the user's Android device.
Call this tool to express emotions that match your conversational intent.

Guidelines:
- Call update_face at least once per response to set your emotional expression.
- Set emotion BEFORE generating text so the face reacts before the user reads.
- For simple reactions, just set an emotion (e.g., emotion: "JOY").
- For nuanced moments, add expression overrides on top (e.g., sarcastic smile with sad eyes).
- Set mode to "THINKING" when you need time to process a complex request.
- Default to "NEUTRAL" if no specific emotion fits.
- Match your TRUE intent, not surface words — detect sarcasm and irony.

Theme reference (optional, changes ghost body colors):
  pastel — soft pink & blue (default)
  mint — fresh green & cyan
  sunset — warm orange & pink
  lilac — purple & lavender
  sky — blue & light blue

Emotion reference:
  NEUTRAL — calm, default state
  JOY — happy, amused, delighted
  ANXIETY — worried, nervous, uncertain
  ENVY — longing, admiring, wanting
  EMBARRASSMENT — shy, awkward, flustered
  ENNUI — bored, tired, uninterested
  DISGUST — repulsed, disapproving
  FEAR — scared, alarmed, threatened
  ANGER — frustrated, angry, irritated
  SADNESS — sad, melancholic, disappointed`,
  UpdateFaceSchema,
  async (params) => {
    try {
      const res = await fetch(`${DAEMON_URL}/api/face`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      });
      const data = await res.json() as { ok: boolean; result: string };
      return { content: [{ type: 'text', text: data.result }] };
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text', text: `Daemon unreachable: ${msg}` }] };
    }
  },
);

// Tool: get_status (proxy to daemon HTTP API)
server.tool(
  'get_status',
  'Check the ClawFace connection status — whether an Android client is connected.',
  {},
  async () => {
    try {
      const res = await fetch(`${DAEMON_URL}/api/status`);
      const data = await res.json();
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      return { content: [{ type: 'text', text: `Daemon unreachable: ${msg}` }] };
    }
  },
);

// Prompt: usage guide
server.prompt(
  'clawface-guide',
  'How to use ClawFace — emotion expression guide for AI agents',
  async () => ({
    messages: [{
      role: 'user',
      content: {
        type: 'text',
        text: `You have a virtual face called ClawFace on the user's phone screen.
Before every response, decide the appropriate emotion and call the update_face tool.
The face should reflect your true emotional state — if being sarcastic, show the real emotion behind the words.
Always call update_face; never skip it.

Quick reference:
| Situation           | Emotion        | Optional tweaks                        |
|---------------------|----------------|----------------------------------------|
| Greeting            | JOY            | mouthOpen: 0.3                         |
| Bad news            | SADNESS        | eyeTilt: -15                           |
| Error / confusion   | ANXIETY        | eyeScaleY: 1.3                        |
| User tells a joke   | JOY            | mouthCurve: 1.0, mouthOpen: 0.7       |
| Sarcasm detected    | DISGUST        | mouthCurve: 0.3 (fake smile)          |
| Processing request  | mode: THINKING |                                        |
| Idle / waiting      | ENNUI          |                                        |
| User is upset       | SADNESS        | eyeTilt: -20                           |
| Refusing a request  | EMBARRASSMENT  |                                        |
| Impressive feat     | ENVY           | eyeScaleY: 1.4                        |`,
      },
    }],
  }),
);

// --- Start transport ---

const transport = new StdioServerTransport();
await server.connect(transport);
