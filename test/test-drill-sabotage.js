import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';
import { runLoop } from '../src/loop.js';
import { loadState } from '../src/state.js';

const projDir = process.cwd();
const forgeDir = path.join(projDir, '.forge');

function cleanUp() {
  if (fs.existsSync(forgeDir)) {
    fs.rmSync(forgeDir, { recursive: true, force: true });
  }
}

async function run() {
  console.log('--- Starting Rehearsal Drill: Sabotage and Recover ---');
  cleanUp();

  console.log('Running end-to-end loop with simulated check sabotage on Task 2...');
  
  await runLoop({
    objective: 'build a gorgeous tip calculator',
    isNew: true,
    projDir,
    mock: true,
    demo: false
  });

  console.log('\nAnalyzing recovered state...');
  const state = loadState(projDir);
  assert.ok(state, 'State should exist on disk.');

  // Prove recovery after multiple attempts on the same task
  const task2 = state.plan.find(t => t.id === '2');
  assert.strictEqual(task2.status, 'done', 'Task 2 must be successfully completed after recovery.');

  // Prove hypotheses are logged and resolved
  console.log(`Registered Hypotheses: ${state.hypotheses.length}`);
  assert.strictEqual(state.hypotheses.length, 2, 'Should have logged exactly 2 distinct fix hypotheses before success.');
  
  assert.strictEqual(state.hypotheses[0].outcome, 'failed', 'First fix attempt outcome should be failed.');
  assert.strictEqual(state.hypotheses[1].outcome, 'success', 'Second fix attempt outcome should be success.');
  
  console.log('🟢 Validation proof: Forge successfully recovered from the injected sabotage.');
  cleanUp();
  console.log('--- Rehearsal Drill: Sabotage and Recover PASSED ---');
}

run().catch(err => {
  console.error('Sabotage drill failed:', err);
  process.exit(1);
});
