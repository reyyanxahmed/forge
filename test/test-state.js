import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';
import { initState, loadState, saveState, reconstructBriefing } from '../src/state.js';

function cleanUp() {
  const forgeDir = path.join(process.cwd(), '.forge');
  if (fs.existsSync(forgeDir)) {
    fs.rmSync(forgeDir, { recursive: true, force: true });
  }
}

async function run() {
  console.log('--- Starting Milestone 2 Verification ---');
  cleanUp();

  const projDir = process.cwd();
  console.log('Initializing new state...');
  let state = initState('build a group expense splitting PWA');

  // Mutate state across 5 simulated iterations
  console.log('Simulating 5 steps/mutations...');
  
  // Step 1: Generate plan
  state.plan = [
    { id: '1', description: 'Write base index.html file', status: 'pending', depends_on: [] },
    { id: '2', description: 'Write app styling', status: 'pending', depends_on: ['1'] }
  ];
  state.session_log.push('Iteration 1: Plan generated.');

  // Step 2: Set Task 1 to in_progress
  state.plan[0].status = 'in_progress';
  state.session_log.push('Iteration 2: Task 1 set to in_progress.');

  // Step 3: Write index.html file to ledger
  state.file_ledger['dist/index.html'] = 'App entry skeleton';
  state.session_log.push('Iteration 3: dist/index.html written.');

  // Step 4: Add build history entry
  state.build_history.push({
    command: 'node run check',
    exit_code: 0,
    stdout: 'Check passed',
    stderr: '',
    timestamp: Date.now()
  });
  state.session_log.push('Iteration 4: Build history appended.');

  // Step 5: Log a failure hypothesis
  state.hypotheses.push({
    error_signature: 'SyntaxError: Unexpected token',
    diagnosis: 'Missing closing curly bracket',
    fix_attempted: 'Added bracket',
    outcome: 'success'
  });
  state.session_log.push('Iteration 5: Failure hypothesis saved.');

  // Save State
  console.log('Saving state atomically...');
  saveState(projDir, state);

  // Assert state file exists
  const statePath = path.join(projDir, '.forge', 'state.json');
  assert.ok(fs.existsSync(statePath), 'state.json should exist on disk.');

  // Simulate process kill (clear in-memory representation)
  console.log('Simulating process crash... clearing in-memory state.');
  state = null;

  // Reload state
  console.log('Loading state from disk...');
  const reloaded = loadState(projDir);
  assert.ok(reloaded, 'Should reload state successfully.');
  
  // Asserts
  assert.strictEqual(reloaded.objective, 'build a group expense splitting PWA', 'Objective should match');
  assert.strictEqual(reloaded.plan[0].status, 'in_progress', 'Task 1 status should be in_progress');
  assert.ok(reloaded.file_ledger['dist/index.html'], 'File ledger should preserve index.html');
  assert.strictEqual(reloaded.session_log.length, 5, 'Session log should have 5 entries');
  assert.strictEqual(reloaded.hypotheses[0].error_signature, 'SyntaxError: Unexpected token', 'Hypotheses should match');
  
  console.log('Verification: State data loaded correctly!');

  // Briefing Reconstruction
  console.log('Reconstructing briefing...');
  const briefing = reconstructBriefing(reloaded);
  console.log('\nGenerated Briefing Output:\n' + briefing + '\n');

  // Assert briefing content matches requirements
  assert.ok(briefing.includes('Completed 0/2 tasks'), 'Briefing should note task progress');
  assert.ok(briefing.includes('Active task (ID: 1): "Write base index.html file"'), 'Briefing should mention the active task');
  assert.ok(briefing.includes('dist/index.html'), 'Briefing should list modified files');
  assert.ok(briefing.includes('1 past failures diagnosed'), 'Briefing should list hypotheses count');

  console.log('Assertion successful: briefing covers active task and status correctly.');
  
  // Clean up
  cleanUp();
  console.log('--- Verification Finished: ALL PASS ---');
}

run().catch(err => {
  console.error('State test failed:', err);
  process.exit(1);
});
