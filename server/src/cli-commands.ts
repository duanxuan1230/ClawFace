import type { Sender } from './tool-handler.js';
import { EMOTIONS, FACE_MODES } from './types.js';
import { emotionFrame, expressionFrame, modeFrame, colorFrame, heartbeatFrame } from './frames.js';

/**
 * Register OpenClaw CLI commands under `openclaw clawface <command>`.
 */
export function registerCliCommands(api: any, sender: Sender): void {
  api.registerCli(
    ({ program }: any) => {
      const cmd = program.command('clawface').description('ClawFace face control');

      cmd
        .command('send-emotion <emotion>')
        .description(`Send emotion frame (${EMOTIONS.join(', ')})`)
        .action(async (emotion: string) => {
          const upper = emotion.toUpperCase();
          if (!EMOTIONS.includes(upper as any)) {
            console.error(`Unknown emotion: ${emotion}. Valid: ${EMOTIONS.join(', ')}`);
            return;
          }
          await sender.send(emotionFrame(upper));
          console.log(`Sent emotion: ${upper}`);
        });

      cmd
        .command('send-mode <mode>')
        .description(`Send mode frame (${FACE_MODES.join(', ')})`)
        .action(async (mode: string) => {
          const upper = mode.toUpperCase();
          if (!FACE_MODES.includes(upper as any)) {
            console.error(`Unknown mode: ${mode}. Valid: ${FACE_MODES.join(', ')}`);
            return;
          }
          await sender.send(modeFrame(upper));
          console.log(`Sent mode: ${upper}`);
        });

      cmd
        .command('send-color <hex>')
        .description('Send color frame (e.g., "#FFDD33")')
        .action(async (hex: string) => {
          await sender.send(colorFrame(hex));
          console.log(`Sent color: ${hex}`);
        });

      cmd
        .command('send-expression <json>')
        .description('Send expression frame (JSON object)')
        .action(async (json: string) => {
          try {
            const params = JSON.parse(json);
            await sender.send(expressionFrame(params));
            console.log(`Sent expression: ${json}`);
          } catch {
            console.error('Invalid JSON');
          }
        });

      cmd
        .command('ping')
        .description('Send a heartbeat frame')
        .action(async () => {
          await sender.send(heartbeatFrame());
          console.log('Sent heartbeat');
        });
    },
    { commands: ['clawface'] },
  );
}
