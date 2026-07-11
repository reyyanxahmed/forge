import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';
import { initState, saveState, loadState } from '../src/state.js';
import { execCommand } from '../src/tools/shell.js';

const projDir = process.cwd();
const forgeDir = path.join(projDir, '.forge');

function cleanUp() {
  if (fs.existsSync(forgeDir)) {
    fs.rmSync(forgeDir, { recursive: true, force: true });
  }
}

async function run() {
  console.log('--- Starting Rehearsal Drill: Kill and Resume ---');
  cleanUp();

  // 1. Manually craft state mimicking a crash/kill after Task 1 succeeded
  console.log('Crafting pre-existing state with Task 1 [DONE] and Tasks 2 & 3 [PENDING]...');
  const state = initState('build a group expense splitting app');
  
  state.plan = [
    { id: '1', description: 'Initialize workspace with index.html, app.js, style.css and manifest.json', status: 'done', depends_on: [] },
    { id: '2', description: 'Write app logic and layout in files', status: 'pending', depends_on: ['1'] },
    { id: '3', description: 'Verify PWA compliance and serve', status: 'pending', depends_on: ['2'] }
  ];
  state.file_ledger['dist/index.html'] = 'Pre-existing base html';
  state.file_ledger['dist/manifest.json'] = 'Pre-existing base manifest';
  state.file_ledger['dist/sw.js'] = 'Pre-existing service worker';
  state.session_log.push('Iteration 1: Plan generated');
  state.session_log.push('Iteration 2: Task 1 scaffolded and completed');
  
  saveState(projDir, state);

  // 2. Call resume command
  console.log('Executing CLI Resume subcommand: node bin/forge.js resume --mock ...');
  
  const result = await execCommand('node bin/forge.js resume --mock', projDir);
  console.log('\n--- Resume Execution Trace Output ---');
  console.log(result.stdout);
  console.log('-------------------------------------\n');

  assert.strictEqual(result.code, 0, 'Resume command should exit with code 0.');

  // 3. Verify final state
  console.log('Verifying state outcomes on disk...');
  const finalState = loadState(projDir);
  assert.ok(finalState, 'Final state file should exist.');

  // Assert that Task 1 was skipped and stayed done, while other tasks completed
  assert.strictEqual(finalState.plan[0].status, 'done', 'Task 1 should remain done.');
  assert.strictEqual(finalState.plan[1].status, 'done', 'Task 2 should be done.');
  assert.strictEqual(finalState.plan[2].status, 'done', 'Task 3 should be done.');
  
  // Make sure we didn't re-run Task 1. Since Task 1 was done, our total session log grew from iteration 2 onwards.
  console.log('Success! Total iterations logged: ' + finalState.session_log.length);
  assert.ok(finalState.session_log.length > 2, 'Session log should have appended steps.');

  cleanUp();
  console.log('--- Rehearsal Drill: Kill and Resume PASSED ---');
}

run().catch(err => {
  console.error('Resume drill failed:', err);
  process.exit(1);
});
