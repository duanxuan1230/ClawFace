import type { Sender } from './tool-handler.js';
import { heartbeatFrame } from './frames.js';

/**
 * Background heartbeat service.
 * Sends {"type":"heartbeat"} at a fixed interval to keep the Android client alive.
 */
export function createHeartbeatService(sender: Sender, intervalMs: number) {
  let timer: ReturnType<typeof setInterval> | null = null;

  return {
    id: 'clawface-heartbeat',

    start() {
      if (timer) return;
      timer = setInterval(async () => {
        try {
          await sender.send(heartbeatFrame());
        } catch {
          // Silently ignore send errors — Android may not be connected yet
        }
      }, intervalMs);
      console.log(`[ClawFace] Heartbeat service started (interval: ${intervalMs}ms)`);
    },

    stop() {
      if (timer) {
        clearInterval(timer);
        timer = null;
        console.log('[ClawFace] Heartbeat service stopped');
      }
    },
  };
}
