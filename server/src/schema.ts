/**
 * JSON Schema for the update_face tool parameters.
 * Constrains LLM output so invalid values never reach the UDP sender.
 */
export const updateFaceSchema = {
  type: 'object' as const,
  required: [] as string[],
  properties: {
    emotion: {
      type: 'string' as const,
      enum: [
        'NEUTRAL', 'JOY', 'ANXIETY', 'ENVY', 'EMBARRASSMENT',
        'ENNUI', 'DISGUST', 'FEAR', 'ANGER', 'SADNESS',
      ],
      description: 'Set the overall emotion preset. Changes color, eyes, and mouth at once.',
    },
    expression: {
      type: 'object' as const,
      description: 'Fine-tune individual face parameters on top of the emotion preset.',
      required: [] as string[],
      properties: {
        eyeScaleY:    { type: 'number' as const, minimum: 0.0, maximum: 1.5, description: 'Eye openness (0=closed, 1=normal, 1.5=wide)' },
        eyeTilt:      { type: 'number' as const, minimum: -30, maximum: 30, description: 'Eye tilt degrees (positive=outer corner up)' },
        eyeSquint:    { type: 'number' as const, minimum: 0.0, maximum: 1.0, description: 'Squinting intensity' },
        pupilOffsetX: { type: 'number' as const, minimum: -1.0, maximum: 1.0, description: 'Pupil horizontal offset' },
        pupilOffsetY: { type: 'number' as const, minimum: -1.0, maximum: 1.0, description: 'Pupil vertical offset' },
        pupilScale:   { type: 'number' as const, minimum: 0.5, maximum: 1.5, description: 'Pupil size multiplier' },
        mouthCurve:   { type: 'number' as const, minimum: -1.0, maximum: 1.0, description: 'Mouth curve (-1=sad frown, 0=flat, 1=happy smile)' },
        mouthWidth:   { type: 'number' as const, minimum: 0.0, maximum: 1.0, description: 'Mouth width' },
        mouthOpen:    { type: 'number' as const, minimum: 0.0, maximum: 1.0, description: 'Mouth openness' },
      },
      additionalProperties: false,
    },
    mode: {
      type: 'string' as const,
      enum: ['ACTIVE', 'STANDBY', 'THINKING', 'OFFLINE'],
      description: 'Set operational mode. Use THINKING when processing, ACTIVE when responding.',
    },
    color: {
      type: 'string' as const,
      pattern: '^#[0-9A-Fa-f]{6}$',
      description: 'Override face color with hex code, e.g. "#FFDD33".',
    },
  },
  additionalProperties: false,
};
