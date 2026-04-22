/**
 * Configuration loaded from environment variables.
 * MCP clients pass these via the `env` field in their server config.
 */

export interface ClawFaceConfig {
  /** "server" = listen for client connections (VPS), "direct" = send to target (LAN) */
  mode: 'server' | 'direct';
  /** server mode: listen port; direct mode: target port */
  port: number;
  /** direct mode: target IP address */
  host: string;
  /** Enable background heartbeat to keep Android client alive */
  heartbeat: boolean;
  /** Heartbeat interval in milliseconds */
  heartbeatIntervalMs: number;
}

export function loadConfig(): ClawFaceConfig {
  return {
    mode: parseMode(process.env.CLAWFACE_MODE),
    port: parseInt(process.env.CLAWFACE_PORT ?? '9527', 10),
    host: process.env.CLAWFACE_HOST ?? '127.0.0.1',
    heartbeat: process.env.CLAWFACE_HEARTBEAT !== 'false',
    heartbeatIntervalMs: parseInt(process.env.CLAWFACE_HEARTBEAT_INTERVAL ?? '30000', 10),
  };
}

function parseMode(value: string | undefined): 'server' | 'direct' {
  if (value === 'direct') return 'direct';
  return 'server';
}
