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
    let startNetwork: () => Promise<void>;
    let stopNetwork: () => void;

    if (config.mode === 'server') {
      const server = new UdpServer(config.listenPort);

      server.onMessage = (msg) => {
        try {
          const frame = JSON.parse(msg);
          if (frame.type === 'heartbeat') {
            server.send(heartbeatAckFrame()).catch(() => {});
          }
        } catch { /* ignore parse errors */ }
      };

      sender = server;
      startNetwork = async () => {
        await server.start();
        console.log(`[ClawFace] Server mode — listening on UDP :${config.listenPort}`);
        console.log('[ClawFace] Waiting for Android client to connect...');
      };
      stopNetwork = () => server.destroy();
    } else {
      const directSender = new UdpSender(config.targetHost, config.targetPort);
      sender = directSender;
      startNetwork = async () => {
        console.log(`[ClawFace] Direct mode — target ${config.targetHost}:${config.targetPort}`);
      };
      stopNetwork = () => directSender.destroy();
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

    // 2. Register background service (starts network + heartbeat on service start)
    const heartbeat = config.enableHeartbeat
      ? createHeartbeatService(sender, config.heartbeatIntervalMs)
      : null;

    try {
      api.registerService({
        id: 'clawface-network',
        start: async () => {
          await startNetwork();
          heartbeat?.start();
        },
        stop: () => {
          heartbeat?.stop();
          stopNetwork();
        },
      });
    } catch (err) {
      console.warn('[ClawFace] Failed to register service:', err);
      // Fallback: start directly if registerService not available
      startNetwork().then(() => heartbeat?.start()).catch(console.error);
    }

    // 3. Register CLI commands
    try {
      registerCliCommands(api, sender);
    } catch (err) {
      console.warn('[ClawFace] Failed to register CLI commands:', err);
    }
  },
};
