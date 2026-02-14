/**
 * Frame builders — output JSON strings matching Android FrameParser.kt exactly.
 * Each function returns a single JSON string (one frame per UDP packet).
 */

export function emotionFrame(emotion: string): string {
  return JSON.stringify({ type: 'emotion', emotion: emotion.toUpperCase() });
}

export function expressionFrame(params: Record<string, number>): string {
  return JSON.stringify({ type: 'expression', params });
}

export function modeFrame(mode: string): string {
  return JSON.stringify({ type: 'mode', mode: mode.toUpperCase() });
}

export function colorFrame(color: string): string {
  return JSON.stringify({ type: 'color', color });
}

export function heartbeatFrame(): string {
  return JSON.stringify({ type: 'heartbeat' });
}

export function heartbeatAckFrame(): string {
  return JSON.stringify({ type: 'heartbeat_ack' });
}
