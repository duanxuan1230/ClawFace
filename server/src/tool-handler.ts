import type { UpdateFaceParams } from './types.js';
import { emotionFrame, expressionFrame, modeFrame, colorFrame } from './frames.js';

/** Anything with a send(data: string) method */
export interface Sender {
  send(data: string): Promise<void>;
}

/**
 * Handle the update_face tool invocation.
 * Decomposes the combined params into separate UDP frames:
 *   emotion (first, resets preset) → mode → color → expression (last, fine-tunes)
 */
export async function handleUpdateFace(
  params: UpdateFaceParams,
  sender: Sender,
): Promise<string> {
  const results: string[] = [];

  // 1. Emotion frame first — sets the base preset on Android side
  if (params.emotion) {
    await sender.send(emotionFrame(params.emotion));
    results.push(`emotion=${params.emotion}`);
  }

  // 2. Mode frame
  if (params.mode) {
    await sender.send(modeFrame(params.mode));
    results.push(`mode=${params.mode}`);
  }

  // 3. Color override
  if (params.color) {
    await sender.send(colorFrame(params.color));
    results.push(`color=${params.color}`);
  }

  // 4. Expression overrides last — fine-tuning on top of emotion preset
  if (params.expression) {
    const exprParams: Record<string, number> = {};
    for (const [key, value] of Object.entries(params.expression)) {
      if (typeof value === 'number') {
        exprParams[key] = value;
      }
    }
    if (Object.keys(exprParams).length > 0) {
      await sender.send(expressionFrame(exprParams));
      results.push(`expression={${Object.keys(exprParams).join(',')}}`);
    }
  }

  if (results.length === 0) {
    return 'No face parameters provided.';
  }

  return `Face updated: ${results.join(', ')}`;
}
