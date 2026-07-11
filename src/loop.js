import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import { initState, loadState, saveState, reconstructBriefing } from './state.js';
import { infer } from './router.js';
import { writeFile, listFiles, readFile } from './tools/files.js';
import { execCommand, normalizeErrorSignature } from './tools/shell.js';
import { validatePWA } from './tools/server.js';
import { benchmarkAndroidProject } from './tools/benchmarker.js';
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

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function loadPrompt(role) {
  const promptPath = path.join(__dirname, 'prompts', `${role}.md`);
  return fs.readFileSync(promptPath, 'utf8');
}

function copyTemplate(projDir, objective) {
  let appName = 'ForgeApp';
  try {
    const cleaned = objective.replace(/[^a-zA-Z0-9\s]/g, '').trim();
    if (cleaned) {
      appName = cleaned.split(/\s+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('');
    }
  } catch (_) {}
  
  traceAct(`Running native Android SDK scaffolding for app: "${appName}"...`);
  try {
    execSync(`/Users/reyyan/.local/bin/android create empty-activity --name="${appName}" -o="${projDir}"`, {
      stdio: ['ignore', 'pipe', 'pipe']
    });
  } catch (err) {
    traceError(`Android scaffolding failed: ${err.message}`);
    process.exit(1);
  }
}

export async function runLoop({ objective, isNew, projDir, mock = false, demo = false, sketchPath = null }) {
  let state;
  const maxIterations = 40;

  if (isNew) {
    state = initState(objective);
    copyTemplate(projDir, objective);
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
      
      const systemPrompt = loadPrompt('planner');
      
      traceDecide('gemma-4-e4b', 'Generating project plan...');
      const response = await infer({
        role: 'planner',
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: `Objective: "${state.objective}". Generate the task graph now.` }
        ],
        expect: 'json',
        mock,
        sketchPath
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
          
          // Execute Quality Benchmarking
          const benchmark = benchmarkAndroidProject(projDir);
          traceCheck(`📊 Forge Quality Benchmarking Score: \x1b[1m\x1b[32m${benchmark.score}/100\x1b[0m`);
          benchmark.logs.forEach(log => {
            console.log(`   ${log}`);
          });

          if (!mock) {
            traceCheck('Executing final APK assembly and physical S23 Ultra deployment...');
            try {
              execSync(`./gradlew assembleDebug`, { cwd: projDir, stdio: 'inherit' });
              const apkPath = path.join(projDir, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
              traceCheck(`APK generated successfully at: ${apkPath}`);
              traceCheck('Deploying APK to physical S23 Ultra...');
              execSync(`/Users/reyyan/.local/bin/android run --apks=${apkPath}`, { stdio: 'inherit' });
              traceCheck('🟢 App successfully installed and launched on Samsung S23 Ultra!');
            } catch (err) {
              traceError(`APK packaging/deployment failed: ${err.message}`);
            }
          }
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
      const coderSystemPrompt = loadPrompt('coder');
      const relevantFiles = files.filter(f => f.endsWith('.kt') || f.endsWith('.kts') || f.endsWith('.xml')).slice(0, 12);
      const fileContents = relevantFiles.map(f => {
        try { return `--- ${f} ---\n${readFile(projDir, f)}`; }
        catch { return ''; }
      }).filter(Boolean).join('\n\n');

      const prompt = `Objective: "${state.objective}".
      Task to execute: "${activeTask.description}".
      Current workspace files and contents:
      ${fileContents}

      Generate file operations to complete this task.`;
      
      traceDecide('gemma-4-e4b', `Generating implementation code for task ${activeTask.id}...`);
      const response = await infer({
        role: 'coder',
        messages: [
          { role: 'system', content: coderSystemPrompt },
          { role: 'user', content: prompt }
        ],
        expect: 'json',
        mock
      });

      proposedOperations = response?.operations || [];
    } else if (activeTask.status === 'failed') {
      // Fail recovery task (Fixer path)
      const lastHistory = state.build_history[state.build_history.length - 1];
      const sig = lastHistory ? normalizeErrorSignature(lastHistory.stderr) : 'unknown error';
      
      const relatedHypotheses = state.hypotheses.filter(h => h.error_signature === sig);

      const fixerSystemPrompt = loadPrompt('fixer');
      const relevantFiles = files.filter(f => f.endsWith('.kt') || f.endsWith('.kts') || f.endsWith('.xml')).slice(0, 12);
      const currentFiles = relevantFiles.map(f => {
        try { return `--- ${f} ---\n${readFile(projDir, f)}`; }
        catch { return ''; }
      }).filter(Boolean).join('\n\n');

      const prompt = `Objective: "${state.objective}".
Task: "${activeTask.description}".
The previous execution failed with error:
"${lastHistory?.stderr || 'Check failed'}"
Signature: "${sig}".

Current file contents:
${currentFiles}

Prior attempted fixes and diagnoses:
${JSON.stringify(relatedHypotheses, null, 2)}

Provide a diagnosis and proposed code adjustments that differ from prior failing attempts.`;

      traceDecide('gemma-4-e4b', `Diagnosing build failure with error signature: "${sig}"...`);
      const response = await infer({
        role: 'fixer',
        messages: [
          { role: 'system', content: fixerSystemPrompt },
          { role: 'user', content: prompt }
        ],
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
      if (state.objective.toLowerCase().includes('sabotage')) {
        const attemptCount = state.hypotheses.filter(h => h.error_signature.includes('sabotageVar')).length;
        if (attemptCount < 3) {
          auditPass = false;
          checkReason = 'Kotlin compilation failed with injected sabotage error.';
          runStderr = 'e: /Users/reyyan/deepmind-hack/app/src/main/java/com/example/testapp/ui/main/MainScreen.kt: (30, 10): Unresolved reference: sabotageVar';
        }
      } else {
        // Let's simulate an error signature failure on the FIRST TWO attempts of Task 2
        if (activeTask.id === '2' && state.hypotheses.length < 2) {
          auditPass = false;
          checkReason = 'Kotlin compilation failed.';
          runStderr = 'e: /Users/reyyan/deepmind-hack/app/src/main/java/com/example/testapp/ui/main/MainScreen.kt: (24, 5): Unresolved reference: activeTab';
        }
      }
    } else {
      // Live validation: run Kotlin compile verification via Gradle
      traceCheck(`Executing local gradle compile check inside: "${projDir}"...`);
      try {
        const gradlewPath = path.join(projDir, 'gradlew');
        try { fs.chmodSync(gradlewPath, 0o755); } catch (_) {}
        
        execSync(`./gradlew compileDebugKotlin`, {
          cwd: projDir,
          stdio: ['ignore', 'pipe', 'pipe']
        });
        auditPass = true;
        checkReason = 'Kotlin code compiled successfully.';
      } catch (err) {
        auditPass = false;
        checkReason = 'Kotlin compilation failed.';
        runStderr = err.stderr ? err.stderr.toString('utf8') : (err.message || 'Unknown compile error');
      }
    }

    state.build_history.push({
      command: 'compile_kotlin',
      exit_code: auditPass ? 0 : 1,
      stdout: checkReason,
      stderr: runStderr,
      timestamp: Date.now()
    });

    // Check with judge (E2B)
    const judgeSystemPrompt = loadPrompt('judge');
    const judgePrompt = `Validation outcome:
Status: ${auditPass ? 'SUCCESS' : 'FAILURE'}
Output/Error: "${checkReason}"
Determine if this task is complete.`;

    traceDecide('gemma-4-e2b', 'Evaluating task completion...');
    const evaluation = await infer({
      role: 'judge',
      messages: [
        { role: 'system', content: judgeSystemPrompt },
        { role: 'user', content: judgePrompt }
      ],
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
        console.log(`\n\x1b[1m\x1b[37m[KNOWS]\x1b[0m Forge generated Android source code, but Kotlin compilation consistently fails with: "${sig}".`);
        console.log(`\x1b[1m\x1b[37m[TRIED]\x1b[0m Attempted 3 different codebase modifications to correct the compilation issue.`);
        console.log(`\x1b[1m\x1b[33m[NEED]\x1b[0m Please provide dynamic guidance or clarification (e.g. "remove sabotageVar from MainScreen" or "disable sync logic"):`);
        
        const rl = readline.createInterface({ input, output });
        const humanGuidance = await rl.question('\n\x1b[1m\x1b[32m[HUMAN INPUT REQUIRED]\x1b[0m > ');
        rl.close();

        if (humanGuidance.trim().toLowerCase() === 'exit') {
          process.exit(1);
        }

        traceDecide('gemma-4-e4b', 'Re-planning task graph with dynamic human feedback...');
        const systemPrompt = loadPrompt('planner');
        
        const replanPrompt = `Objective: "${state.objective}".
Current plan status: ${JSON.stringify(state.plan, null, 2)}
The task "${activeTask.description}" has failed repeatedly with compilation error: "${sig}".
The developer provided this manual guidance/clarification: "${humanGuidance}"

Your job is to revise the task graph. You can add new sub-tasks to guide the coder, modify pending tasks, or mark existing tasks for retry. Return a revised task list in the required JSON schema.`;

        const response = await infer({
          role: 'planner',
          messages: [
            { role: 'system', content: systemPrompt },
            { role: 'user', content: replanPrompt }
          ],
          expect: 'json',
          mock
        });
        
        if (response && Array.isArray(response.tasks)) {
          state.plan = response.tasks.map(t => ({
            id: t.id,
            description: t.description,
            status: t.status || 'pending',
            depends_on: t.depends_on || []
          }));
          state.hypotheses = []; // Reset hypotheses for next attempts
          traceAct(`Successfully re-planned and injected ${state.plan.length} tasks! Continuing loop...`);
          saveState(projDir, state);
          continue;
        } else {
          traceError('Malformed re-planning response from planner.');
          process.exit(1);
        }
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
