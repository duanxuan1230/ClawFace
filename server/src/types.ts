/** Must match android FaceState.kt Emotion enum */
export const EMOTIONS = [
  'NEUTRAL', 'JOY', 'ANXIETY', 'ENVY', 'EMBARRASSMENT',
  'ENNUI', 'DISGUST', 'FEAR', 'ANGER', 'SADNESS',
] as const;
export type Emotion = (typeof EMOTIONS)[number];

/** Must match android FaceState.kt FaceMode enum */
export const FACE_MODES = ['ACTIVE', 'STANDBY', 'THINKING', 'OFFLINE'] as const;
export type FaceMode = (typeof FACE_MODES)[number];

/** Must match android GhostTheme.kt theme names */
export const GHOST_THEMES = ['pastel', 'mint', 'sunset', 'lilac', 'sky'] as const;
export type GhostThemeName = (typeof GHOST_THEMES)[number];

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
  theme?: GhostThemeName;
}

/** Anything with a send(data: string) method. */
export interface Sender {
  send(data: string): Promise<void>;
}
