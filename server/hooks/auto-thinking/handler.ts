import { getSender } from '../../src/sender-ref.js';
import { modeFrame } from '../../src/frames.js';

const handler = async (event: any) => {
  if (event.type !== 'agent' || event.action !== 'bootstrap') return;

  const sender = getSender();
  if (!sender) return;

  try {
    await sender.send(modeFrame('THINKING'));
  } catch {
    // Silently ignore — client may not be connected yet
  }
};

export default handler;
