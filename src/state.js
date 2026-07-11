import fs from 'node:fs';
import path from 'node:path';

export function loadState(projDir) {
  const statePath = path.join(projDir, '.forge', 'state.json');
  if (!fs.existsSync(statePath)) {
    return null;
  }
  try {
    const raw = fs.readFileSync(statePath, 'utf8');
    return JSON.parse(raw);
  } catch (err) {
    // Tolerates potential incomplete writes
    const tmpPath = statePath + '.tmp';
    if (fs.existsSync(tmpPath)) {
      try {
        const rawTmp = fs.readFileSync(tmpPath, 'utf8');
        return JSON.parse(rawTmp);
      } catch (_) {}
    }
    return null;
  }
}

export function saveState(projDir, state) {
  // Skeleton stub
}

export function reconstructBriefing(state) {
  return '';
}
