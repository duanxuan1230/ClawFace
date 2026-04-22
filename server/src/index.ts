#!/usr/bin/env node
/**
 * ClawFace MCP Server
 *
 * Exposes the `update_face` and `get_status` tools via the Model Context Protocol.
 * Any MCP-compatible AI agent can use these to control the ClawFace Android desktop pet.
 *
 * Configuration via environment variables:
 *   CLAWFACE_MODE=server|direct   (default: server)
 *   CLAWFACE_PORT=9527            (default: 9527)
 *   CLAWFACE_HOST=127.0.0.1      (default: 127.0.0.1, direct mode only)
 *   CLAWFACE_HEARTBEAT=true       (default: true)
 *   CLAWFACE_HEARTBEAT_INTERVAL=30000  (default: 30000ms)
 */

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { loadConfig } from './config.js';
import { UpdateFaceSchema } from './schemas.js';
import { UdpSender } from './udp-sender.js';
import { UdpServer } from './udp-server.js';
import { handleUpdateFace } from './tool-handler.js';
import { createHeartbeatService } from './heartbeat-service.js';
import { heartbeatAckFrame } from './frames.js';
import type { Sender } from './types.js';

const config = loadConfig();

// --- Network layer ---

let sender: Sender;
let cleanup: () => void;

if (config.mode === 'server') {
  const server = new UdpServer(config.port);
  server.onMessage = (msg) => {
    try {
      const frame = JSON.parse(msg);
      if (frame.type === 'heartbeat') {
        server.send(heartbeatAckFrame()).catch(() => {});
      }
    } catch { /* ignore parse errors */ }
  };
  await server.start();
  console.error(`[ClawFace] Server mode — listening on UDP :${config.port}`);

  sender = server;
  cleanup = () => server.destroy();
} else {
  const direct = new UdpSender(config.host, config.port);
  console.error(`[ClawFace] Direct mode — target ${config.host}:${config.port}`);

  sender = direct;
  cleanup = () => direct.destroy();
}

// --- Heartbeat ---

const heartbeat = config.heartbeat
  ? createHeartbeatService(sender, config.heartbeatIntervalMs)
  : null;
heartbeat?.start();

// --- MCP Server ---

const server = new McpServer({
  name: 'clawface',
  version: '1.0.0',
});

// Tool: update_face
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
    const result = await handleUpdateFace(params, sender);
    return { content: [{ type: 'text', text: result }] };
  },
);

// Tool: get_status
server.tool(
  'get_status',
  'Check the ClawFace connection status — whether an Android client is connected and the current network mode.',
  {},
  async () => {
    const mode = config.mode;
    const connected = config.mode === 'server'
      ? (sender as UdpServer).hasClient()
      : true; // direct mode always "connected" (best-effort UDP)
    const clientInfo = config.mode === 'server'
      ? (sender as UdpServer).getClientInfo()
      : `${config.host}:${config.port}`;

    return {
      content: [{
        type: 'text',
        text: JSON.stringify({ mode, connected, client: clientInfo }, null, 2),
      }],
    };
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

// --- Graceful shutdown ---

process.on('SIGINT', () => {
  heartbeat?.stop();
  cleanup();
  process.exit(0);
});

process.on('SIGTERM', () => {
  heartbeat?.stop();
  cleanup();
  process.exit(0);
});
