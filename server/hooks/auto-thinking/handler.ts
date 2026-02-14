import { getSender } from '../../src/sender-ref.js';
import { modeFrame } from '../../src/frames.js';

export default async function handler() {
  const sender = getSender();
  if (!sender) return;

  try {
    await sender.send(modeFrame('THINKING'));
  } catch {
    // Silently ignore — client may not be connected yet
  }
}
