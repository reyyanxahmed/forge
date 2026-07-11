import fs from 'node:fs';
import path from 'node:path';
import { initState, loadState, saveState, reconstructBriefing } from './state.js';
import { infer } from './router.js';
import { writeFile, listFiles, readFile } from './tools/files.js';
import { execCommand, normalizeErrorSignature } from './tools/shell.js';
import { validatePWA } from './tools/server.js';
import {
  traceSense,
  traceDecide,
  traceAct,
  traceCheck,
  traceError,
  traceEscalation
} from './trace.js';

async function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export async function runLoop({ objective, isNew, projDir, mock = false, demo = false }) {
  let state;
  const maxIterations = 40;

  if (isNew) {
    state = initState(objective);
    saveState(projDir, state);
    traceSense(`Starting fresh project with objective: "${objective}"`);
  } else {
    state = loadState(projDir);
    if (!state) {
      traceError('No active project found in this directory.');
      process.exit(1);
    }
    const briefing = reconstructBriefing(state);
    traceSense(`Resuming existing project...\n${briefing}`);
  }

  let iteration = state.session_log.length;

  while (iteration < maxIterations) {
    iteration++;
    state.session_log.push(`Iteration ${iteration}`);

    if (demo) await delay(200);

    // ==========================================
    // 1. SENSE
    // ==========================================
    traceSense(`--- Iteration ${iteration} ---`);
    const files = listFiles(projDir);
    
    // Check if we need to generate a plan first
    if (state.plan.length === 0) {
      traceSense('No plan exists yet. Requesting task plan...');
      
      const prompt = `You are an expert project planner. Please generate a task graph to accomplish this objective: "${state.objective}". Ensure tasks are ordered logically with proper dependency mappings. Return raw JSON matching this schema: { "tasks": [{ "id": "1", "description": "task desc", "depends_on": [] }] }`;
      
      traceDecide('gemma-4-e4b', 'Generating project plan...');
      const response = await infer({
        role: 'planner',
        messages: [{ role: 'user', content: prompt }],
        expect: 'json',
        mock
      });

      if (response && Array.isArray(response.tasks)) {
        state.plan = response.tasks.map(t => ({
          id: t.id,
          description: t.description,
          status: 'pending',
          depends_on: t.depends_on || []
        }));
        traceAct(`Created plan with ${state.plan.length} tasks.`);
        saveState(projDir, state);
        continue; // Proceed to next iteration to work on the first task
      } else {
        traceError('Malformed plan response from planner.');
        process.exit(1);
      }
    }

    // Find active task
    let activeTask = state.plan.find(t => t.status === 'in_progress' || t.status === 'failed');
    
    if (!activeTask) {
      // Find a pending task whose dependencies are all 'done'
      const nextTask = state.plan.find(t => {
        if (t.status !== 'pending') return false;
        return t.depends_on.every(depId => {
          const depTask = state.plan.find(pt => pt.id === depId);
          return depTask && depTask.status === 'done';
        });
      });

      if (!nextTask) {
        // All tasks completed?
        const incomplete = state.plan.filter(t => t.status !== 'done');
        if (incomplete.length === 0) {
          traceCheck('🏆 All tasks completed successfully! Forge has built your application.');
          break;
        } else {
          traceError('Deadlock detected in task dependencies or task stalled.');
          process.exit(1);
        }
      }

      nextTask.status = 'in_progress';
      activeTask = nextTask;
      saveState(projDir, state);
    }

    traceSense(`Working on active task [ID: ${activeTask.id}]: "${activeTask.description}"`);

    // ==========================================
    // 2. DECIDE
    // ==========================================
    let proposedOperations = [];
    let explanation = '';

    if (activeTask.status === 'in_progress') {
      // Direct implementation task
      const prompt = `Objective: "${state.objective}".
Task to execute: "${activeTask.description}".
Existing files list: [${files.join(', ')}].
Please generate the required file operations (write or edit files in "dist/") to complete this task. Return ONLY a JSON object:
{ "operations": [{ "op": "write", "path": "dist/filename", "content": "file contents" }] }`;

      traceDecide('gemma-4-e4b', `Generating implementation code for task ${activeTask.id}...`);
      const response = await infer({
        role: 'coder',
        messages: [{ role: 'user', content: prompt }],
        expect: 'json',
        mock
      });

      proposedOperations = response?.operations || [];
    } else if (activeTask.status === 'failed') {
      // Fail recovery task (Fixer path)
      const lastHistory = state.build_history[state.build_history.length - 1];
      const sig = lastHistory ? normalizeErrorSignature(lastHistory.stderr) : 'unknown error';
      
      const relatedHypotheses = state.hypotheses.filter(h => h.error_signature === sig);

      const prompt = `Objective: "${state.objective}".
Task: "${activeTask.description}".
The previous execution failed with error:
"${lastHistory?.stderr || 'Check failed'}"
Signature: "${sig}".

Prior attempted fixes and diagnoses:
${JSON.stringify(relatedHypotheses, null, 2)}

Provide a diagnosis explanation and proposed code adjustments that differ from prior failing attempts. Return ONLY a JSON object:
{
  "explanation": "diagnosis and fix plan",
  "operations": [{ "op": "write", "path": "dist/filename", "content": "adjusted file contents" }]
}`;

      traceDecide('gemma-4-e4b', `Diagnosing build failure with error signature: "${sig}"...`);
      const response = await infer({
        role: 'fixer',
        messages: [{ role: 'user', content: prompt }],
        expect: 'json',
        mock
      });

      explanation = response?.explanation || 'Attempting to patch syntax error';
      proposedOperations = response?.operations || [];

      // Record hypothesis
      state.hypotheses.push({
        error_signature: sig,
        diagnosis: explanation,
        fix_attempted: `Modified ${proposedOperations.map(o => o.path).join(', ')}`,
        outcome: 'pending_check'
      });
    }

    // ==========================================
    // 3. ACT
    // ==========================================
    if (proposedOperations.length === 0) {
      traceAct('No operations proposed. Advancing task status.');
    } else {
      for (const op of proposedOperations) {
        if (op.op === 'write') {
          traceAct(`Writing file: "${op.path}"...`);
          writeFile(projDir, op.path, op.content);
          state.file_ledger[op.path] = `Written/modified during task ID ${activeTask.id}`;
        }
      }
    }

    saveState(projDir, state);

    // ==========================================
    // 4. CHECK
    // ==========================================
    traceCheck(`Verifying modifications made for task ${activeTask.id}...`);
    
    // We run an offline check. If it's a PWA server verification task, run validatePWA.
    // Otherwise, simulate checking via mock or running local test file if exists.
    let auditPass = true;
    let checkReason = 'Modifications applied cleanly.';
    let runStderr = '';

    if (mock) {
      // In mock mode, we trigger simulated errors based on fixtures
      // Coder/Fixer mock responses will drive this.
      const lastHistory = state.build_history[state.build_history.length - 1];
      
      // Let's simulate an error signature failure on the FIRST TWO attempts of Task 2
      if (activeTask.id === '2' && state.hypotheses.length < 2) {
        auditPass = false;
        checkReason = 'ReferenceError: activeTab is not defined';
        runStderr = 'ReferenceError: activeTab is not defined\n   at calculateBill (dist/app.js:24:5)';
      }
    } else {
      // Live validation: run PWA readiness validator
      const pwaResult = validatePWA(projDir);
      auditPass = pwaResult.isValid;
      if (!auditPass) {
        const broken = pwaResult.checklist.filter(c => !c.status);
        checkReason = `PWA compliance failure: missing registered assets`;
        runStderr = `PWA checklist fails:\n` + pwaResult.checklist.map(c => ` - ${c.label}: ${c.status ? 'PASS' : 'FAIL'}`).join('\n');
      }
    }

    state.build_history.push({
      command: 'validate_pwa',
      exit_code: auditPass ? 0 : 1,
      stdout: checkReason,
      stderr: runStderr,
      timestamp: Date.now()
    });

    // Check with judge (E2B)
    const judgePrompt = `Evaluate this validation outcome:
Status: ${auditPass ? 'SUCCESS' : 'FAILURE'}
Output/Error: "${checkReason}"
Are we ready to mark this task complete? Return ONLY: { "verdict": "pass" | "fail", "reason": "<one line explanation>" }`;

    traceDecide('gemma-4-e2b', 'Evaluating task completion...');
    const evaluation = await infer({
      role: 'judge',
      messages: [{ role: 'user', content: judgePrompt }],
      expect: 'json',
      mock
    });

    const isPass = evaluation?.verdict === 'pass';

    if (isPass) {
      traceCheck(`🟢 Success: Task ${activeTask.id} validated successfully.`);
      activeTask.status = 'done';
      
      // Resolve hypotheses that succeeded
      state.hypotheses.forEach(h => {
        if (h.outcome === 'pending_check') h.outcome = 'success';
      });
    } else {
      const sig = normalizeErrorSignature(runStderr || checkReason);
      traceError(`🔴 Failure: Task ${activeTask.id} validation failed. Error Signature: "${sig}"`);
      
      // Count attempts of this specific signature on this active task
      const attemptCount = state.hypotheses.filter(h => h.error_signature === sig).length;
      
      if (attemptCount >= 3) {
        // ESCALATION RULE: Hit retry limit of 3
        activeTask.status = 'escalated';
        state.escalations.push({
          question: `I have attempted to resolve this error signature 3 times and hit my offline recovery limit.`,
          context: `Error: ${sig}. Tried fixes: ${JSON.stringify(state.hypotheses.filter(h => h.error_signature === sig))}`
        });
        saveState(projDir, state);

        traceEscalation(`Forge Escalation: Offline budget exhausted (Limit 3)`);
        console.log(`\x1b[1m\x1b[37m[KNOWS]\x1b[0m Forge built dist assets, but PWA validation consistently fails with: "${sig}".`);
        console.log(`\x1b[1m\x1b[37m[TRIED]\x1b[0m Attempted 3 different codebase modifications to correct the reference boundaries.`);
        console.log(`\x1b[1m\x1b[37m[NEED]\x1b[0m Please correct the reference in dist/app.js directly or provide a structural template.`);
        process.exit(1);
      }

      activeTask.status = 'failed';
      state.hypotheses.forEach(h => {
        if (h.outcome === 'pending_check') h.outcome = 'failed';
      });
    }

    saveState(projDir, state);
  }

  if (iteration >= maxIterations) {
    traceEscalation(`Global loop limit reached (${maxIterations} iterations)`);
    process.exit(1);
  }
}
