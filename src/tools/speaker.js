import { exec } from 'node:child_process';

let isVoiceEnabled = false;

export function enableVoice(enable = true) {
  isVoiceEnabled = enable;
}

export function speak(text) {
  if (!isVoiceEnabled) return;
  
  if (process.platform === 'darwin') {
    const cleaned = text
      .replace(/[&*()|;<>[\]$"]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (cleaned) {
      exec(`say "${cleaned}" &`);
    }
  }
}
