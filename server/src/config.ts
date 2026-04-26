/**
 * Daemon configuration loaded from environment variables.
 */

export interface ClawFaceConfig {
  /** Listen port for HTTP + WebSocket */
  port: number;
}

export function loadConfig(): ClawFaceConfig {
  return {
    port: parseInt(process.env.CLAWFACE_PORT ?? '9527', 10),
  };
}
