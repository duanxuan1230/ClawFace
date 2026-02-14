import { UdpSender } from './src/udp-sender.js';
import { UdpServer } from './src/udp-server.js';
import { updateFaceSchema } from './src/schema.js';
import { TOOL_DESCRIPTION } from './src/prompt.js';
import { handleUpdateFace } from './src/tool-handler.js';
import type { Sender } from './src/tool-handler.js';
import { createHeartbeatService } from './src/heartbeat-service.js';
import { registerCliCommands } from './src/cli-commands.js';
import { heartbeatAckFrame } from './src/frames.js';
import type { ClawFaceConfig } from './src/types.js';
import { DEFAULT_CONFIG } from './src/types.js';

export default {
  id: 'clawface',
  name: 'ClawFace',

  register(api: any) {
    const userConfig = api.getConfig?.() ?? {};
    const config: ClawFaceConfig = { ...DEFAULT_CONFIG, ...userConfig };

    let sender: Sender;
    let cleanup: () => void;

    if (config.mode === 'server') {
      // Server mode: listen on port, auto-detect client from incoming packets
      const server = new UdpServer(config.listenPort);

      server.onMessage = (msg) => {
        // Auto-respond to heartbeat from Android client
        try {
          const frame = JSON.parse(msg);
          if (frame.type === 'heartbeat') {
            server.send(heartbeatAckFrame()).catch(() => {});
          }
        } catch { /* ignore parse errors */ }
      };

      // Start listening (async, but register is sync — fire and forget)
      server.start().then(() => {
        console.log(`[ClawFace] Server mode — listening on UDP :${config.listenPort}`);
        console.log('[ClawFace] Waiting for Android client to connect...');
      }).catch((err) => {
        console.error('[ClawFace] Failed to start UDP server:', err);
      });

      sender = server;
      cleanup = () => server.destroy();
    } else {
      // Direct mode: send to specific host:port (LAN testing)
      const directSender = new UdpSender(config.targetHost, config.targetPort);
      console.log(`[ClawFace] Direct mode — target ${config.targetHost}:${config.targetPort}`);
      sender = directSender;
      cleanup = () => directSender.destroy();
    }

    // 1. Register the LLM-callable tool
    try {
      api.registerTool({
        name: 'update_face',
        description: TOOL_DESCRIPTION,
        parameters: updateFaceSchema,
        async execute(params: any) {
          return await handleUpdateFace(params, sender);
        },
      });
      console.log('[ClawFace] Registered tool: update_face');
    } catch (err) {
      console.warn('[ClawFace] Failed to register tool:', err);
    }

    // 2. Register heartbeat background service
    if (config.enableHeartbeat) {
      const heartbeat = createHeartbeatService(sender, config.heartbeatIntervalMs);
      try {
        api.registerService({
          id: 'clawface-heartbeat',
          start: () => heartbeat.start(),
          stop: () => {
            heartbeat.stop();
            cleanup();
          },
        });
      } catch (err) {
        console.warn('[ClawFace] Failed to register service:', err);
        heartbeat.start();
      }
    }

    // 3. Register CLI commands
    try {
      registerCliCommands(api, sender);
    } catch (err) {
      console.warn('[ClawFace] Failed to register CLI commands:', err);
    }
  },
};
