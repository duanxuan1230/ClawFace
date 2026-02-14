import type { Sender } from './tool-handler.js';

let _sender: Sender | null = null;

export function setSender(s: Sender): void {
  _sender = s;
}

export function getSender(): Sender | null {
  return _sender;
}
