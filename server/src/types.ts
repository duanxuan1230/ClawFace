/** Must match android FaceState.kt Emotion enum */
export const EMOTIONS = [
  'NEUTRAL', 'JOY', 'ANXIETY', 'ENVY', 'EMBARRASSMENT',
  'ENNUI', 'DISGUST', 'FEAR', 'ANGER', 'SADNESS',
] as const;
export type Emotion = (typeof EMOTIONS)[number];

/** Must match android FaceState.kt FaceMode enum */
export const FACE_MODES = ['ACTIVE', 'STANDBY', 'THINKING', 'OFFLINE'] as const;
export type FaceMode = (typeof FACE_MODES)[number];

/** Expression params — keys must match FrameParser.parseExpression() */
export interface ExpressionParams {
  eyeScaleY?: number;
  eyeTilt?: number;
  eyeSquint?: number;
  pupilOffsetX?: number;
  pupilOffsetY?: number;
  pupilScale?: number;
  mouthCurve?: number;
  mouthWidth?: number;
  mouthOpen?: number;
}

/** Combined parameter object the LLM passes to update_face */
export interface UpdateFaceParams {
  emotion?: Emotion;
  expression?: ExpressionParams;
  mode?: FaceMode;
  color?: string;
}

/** Plugin config */
export interface ClawFaceConfig {
  /** Direct mode: send to this IP (for LAN testing) */
  targetHost: string;
  /** Direct mode: send to this port */
  targetPort: number;
  /** Server mode: listen on this port, auto-detect client from incoming packets */
  listenPort: number;
  /** "server" = listen + auto-detect client (for VPS), "direct" = send to targetHost:targetPort (for LAN) */
  mode: 'server' | 'direct';
  heartbeatIntervalMs: number;
  enableHeartbeat: boolean;
}

export const DEFAULT_CONFIG: ClawFaceConfig = {
  targetHost: '127.0.0.1',
  targetPort: 9527,
  listenPort: 9527,
  mode: 'server',
  heartbeatIntervalMs: 30_000,
  enableHeartbeat: true,
};
