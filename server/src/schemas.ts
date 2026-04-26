import { z } from 'zod';
import { EMOTIONS, FACE_MODES, GHOST_THEMES } from './types.js';

/** Zod schema for the update_face tool parameters. */
export const UpdateFaceSchema = {
  emotion: z
    .enum(EMOTIONS)
    .optional()
    .describe('Set the overall emotion preset. Changes color, eyes, and mouth at once.'),

  expression: z
    .object({
      eyeScaleY: z.number().min(0).max(1.5).optional().describe('Eye openness (0=closed, 1=normal, 1.5=wide)'),
      eyeTilt: z.number().min(-30).max(30).optional().describe('Eye tilt degrees (positive=outer corner up)'),
      eyeSquint: z.number().min(0).max(1).optional().describe('Squinting intensity'),
      pupilOffsetX: z.number().min(-1).max(1).optional().describe('Pupil horizontal offset'),
      pupilOffsetY: z.number().min(-1).max(1).optional().describe('Pupil vertical offset'),
      pupilScale: z.number().min(0.5).max(1.5).optional().describe('Pupil size multiplier'),
      mouthCurve: z.number().min(-1).max(1).optional().describe('Mouth curve (-1=sad, 0=flat, 1=smile)'),
      mouthWidth: z.number().min(0).max(1).optional().describe('Mouth width'),
      mouthOpen: z.number().min(0).max(1).optional().describe('Mouth openness'),
    })
    .optional()
    .describe('Fine-tune individual face parameters on top of the emotion preset.'),

  mode: z
    .enum(FACE_MODES)
    .optional()
    .describe('Set operational mode. THINKING=processing, ACTIVE=responding, STANDBY=idle, OFFLINE=disconnected.'),

  color: z
    .string()
    .regex(/^#[0-9A-Fa-f]{6}$/)
    .optional()
    .describe('Override face color with hex code, e.g. "#FFDD33".'),

  theme: z
    .enum(GHOST_THEMES)
    .optional()
    .describe('Set the ghost body color theme: pastel (pink-blue), mint (green), sunset (orange-pink), lilac (purple), sky (blue).'),
};
