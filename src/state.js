import fs from 'node:fs';
import path from 'node:path';

export function initState(objective) {
  return {
    objective,
    plan: [],           // ordered task graph: { id, description, status, depends_on }
    file_ledger: {},    // { [relPath]: purpose_annotation }
    build_history: [],  // { command, exit_code, stdout, stderr, timestamp }
    hypotheses: [],     // { error_signature, diagnosis, fix_attempted, outcome }
    escalations: [],    // { question, context }
    session_log: []     // [ 'Iteration 1: Sense - ...', 'Iteration 2: Act - ...' ]
  };
}

export function loadState(projDir) {
  const forgeDir = path.join(projDir, '.forge');
  const statePath = path.join(forgeDir, 'state.json');
  const tmpPath = statePath + '.tmp';

  // Helper to safely load and parse a file
  const tryParse = (filePath) => {
    try {
      if (fs.existsSync(filePath)) {
        const content = fs.readFileSync(filePath, 'utf8').trim();
        if (content) {
          return JSON.parse(content);
        }
      }
    } catch (_) {}
    return null;
  };

  // Try standard file first
  let state = tryParse(statePath);
  if (state) return state;

  // Fallback to tmp file if state.json is corrupt or missing
  state = tryParse(tmpPath);
  if (state) {
    try {
      fs.renameSync(tmpPath, statePath);
    } catch (_) {}
    return state;
  }

  return null;
}

export function saveState(projDir, state) {
  const forgeDir = path.join(projDir, '.forge');
  if (!fs.existsSync(forgeDir)) {
    fs.mkdirSync(forgeDir, { recursive: true });
  }

  const statePath = path.join(forgeDir, 'state.json');
  const tmpPath = statePath + '.tmp';

  const serialized = JSON.stringify(state, null, 2);

  // Atomic write: write to .tmp, then atomically rename
  fs.writeFileSync(tmpPath, serialized, 'utf8');
  fs.renameSync(tmpPath, statePath);
}

export function reconstructBriefing(state) {
  if (!state) return 'No active session state to reconstruct briefing from.';

  const totalTasks = state.plan.length;
  const doneTasks = state.plan.filter(t => t.status === 'done').length;
  const activeTask = state.plan.find(t => t.status === 'in_progress' || t.status === 'failed');

  const progressStr = totalTasks > 0 
    ? `Completed ${doneTasks}/${totalTasks} tasks.`
    : 'No plan generated yet.';

  const activeStr = activeTask 
    ? `Active task (ID: ${activeTask.id}): "${activeTask.description}".` 
    : 'Waiting for next planned task selection.';

  const fileCount = Object.keys(state.file_ledger || {}).length;
  const filesStr = fileCount > 0
    ? `Modified files: [${Object.keys(state.file_ledger).join(', ')}].`
    : 'No files modified yet.';

  // Return a compact, 3-line re-grounding briefing
  return `[Briefing] Objective: "${state.objective}"\n` +
         `[Briefing] Progress: ${progressStr} ${activeStr}\n` +
         `[Briefing] Context: ${filesStr} ${state.hypotheses.length} past failures diagnosed.`;
}
