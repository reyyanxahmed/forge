import path from 'node:path';
import { speak } from './tools/speaker.js';

function checkDemoDelay() {
  const isDemo = process.argv.includes('--demo') || process.argv.includes('-d');
  if (isDemo) {
    try {
      // 200ms synchronous block using native Atomics.wait
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 200);
    } catch {
      // Fallback
    }
  }
}

export function printTaskGraph(state) {
  if (!state || !state.plan || state.plan.length === 0) {
    console.log('\x1b[33mNo tasks planned yet.\x1b[0m');
    return;
  }
  
  console.log('\n\x1b[1m\x1b[34m┌── Forge Project Status ──────────────────────────────────────────┐\x1b[0m');
  console.log(`\x1b[1m\x1b[34m│\x1b[0m \x1b[1mObjective:\x1b[0m "${state.objective}"`);
  console.log('\x1b[1m\x1b[34m├── Task Graph ────────────────────────────────────────────────────┤\x1b[0m');
  
  state.plan.forEach((task, index) => {
    const isLast = index === state.plan.length - 1;
    const connector = isLast ? '└──' : '├──';
    
    let statusColor = '\x1b[90m'; // dark gray
    let statusLabel = 'PENDING';
    
    if (task.status === 'done') {
      statusColor = '\x1b[32m'; // green
      statusLabel = '✓ DONE';
    } else if (task.status === 'in_progress') {
      statusColor = '\x1b[33m'; // yellow
      statusLabel = '⚙ WORKING';
    } else if (task.status === 'failed') {
      statusColor = '\x1b[31m'; // red
      statusLabel = '✗ FAILED';
    } else if (task.status === 'escalated') {
      statusColor = '\x1b[1m\x1b[37m'; // bold white
      statusLabel = '⚠ ESCALATED';
    }
    
    console.log(`\x1b[1m\x1b[34m│\x1b[0m  ${connector} [ID: ${task.id}] ${task.description} (${statusColor}${statusLabel}\x1b[0m)`);
  });
  
  console.log('\x1b[1m\x1b[34m└──────────────────────────────────────────────────────────────────┘\x1b[0m\n');
}

export function traceSense(msg) {
  checkDemoDelay();
  console.log(`\x1b[36m[Sense]\x1b[0m ${msg}`);
  if (msg.includes('objective')) {
    speak("Sensing new objective.");
  } else if (msg.includes('Working on active task')) {
    const taskDesc = msg.match(/"([^"]+)"/)?.[1] || "active task";
    speak(`Working on task, ${taskDesc}`);
  }
}

export function traceDecide(model, msg) {
  checkDemoDelay();
  console.log(`\x1b[33m[Decide] [${model}]\x1b[0m ${msg}`);
  if (msg.includes('Generating project plan')) {
    speak("Planning project layout.");
  } else if (msg.includes('Generating implementation')) {
    speak("Synthesizing source code.");
  } else if (msg.includes('Diagnosing build failure')) {
    speak("Diagnosing build error.");
  }
}

export function traceAct(msg) {
  checkDemoDelay();
  console.log(`\x1b[32m[Act]\x1b[0m ${msg}`);
  if (msg.includes('scaffolding')) {
    speak("Scaffolding native Android application.");
  } else if (msg.includes('Writing file')) {
    const file = msg.match(/"([^"]+)"/)?.[1] || "file";
    speak(`Writing ${path.basename(file)}`);
  }
}

export function traceCheck(msg) {
  checkDemoDelay();
  console.log(`\x1b[35m[Check]\x1b[0m ${msg}`);
  if (msg.includes('Verifying modifications')) {
    speak("Verifying modifications.");
  } else if (msg.includes('Executing local gradle')) {
    speak("Executing local Gradle compiler checks.");
  } else if (msg.includes('validated successfully')) {
    speak("Verification passed.");
  } else if (msg.includes('completed successfully')) {
    speak("All tasks completed successfully!");
  }
}

export function traceError(msg) {
  checkDemoDelay();
  console.log(`\x1b[31m[Failure]\x1b[0m ${msg}`);
  speak("Execution failed.");
}

export function traceEscalation(msg) {
  checkDemoDelay();
  console.log(`\x1b[1m\x1b[37m[Escalation]\x1b[0m ${msg}`);
  speak("Recovery budget exhausted. Escalating to developer.");
}

export function explainTimeline(state) {
  if (!state) {
    console.log('\x1b[31mNo state history to explain.\x1b[0m');
    return;
  }
  
  console.log('\n\x1b[1m\x1b[35m┌── Forge Timeline Explainer & Replay Rehearsal ───────────────────┐\x1b[0m');
  console.log(`\x1b[1m\x1b[35m│\x1b[0m 🎯 \x1b[1mGoal:\x1b[0m "${state.objective}"`);
  console.log('\x1b[1m\x1b[35m├── Step 1: Sequential Execution Plan ─────────────────────────────┤\x1b[0m');
  
  if (state.plan && state.plan.length > 0) {
    state.plan.forEach(t => {
      const statusIcon = t.status === 'done' ? '✅' : t.status === 'failed' ? '❌' : '⏳';
      console.log(`\x1b[1m\x1b[35m│\x1b[0m   ${statusIcon} [ID: ${t.id}] ${t.description}`);
    });
  } else {
    console.log('\x1b[1m\x1b[35m│\x1b[0m   (No plan generated yet)');
  }
  
  console.log('\x1b[1m\x1b[35m├── Step 2: Codebase Mod Ledger ───────────────────────────────────┤\x1b[0m');
  const files = Object.keys(state.file_ledger || {});
  if (files.length > 0) {
    files.forEach(file => {
      console.log(`\x1b[1m\x1b[35m│\x1b[0m   📂 Written: ${file} (${state.file_ledger[file]})`);
    });
  } else {
    console.log('\x1b[1m\x1b[35m│\x1b[0m   (No files modified yet)');
  }

  console.log('\x1b[1m\x1b[35m├── Step 3: Self-Healing & Cognitive Diagnostics ──────────────────┤\x1b[0m');
  if (state.hypotheses && state.hypotheses.length > 0) {
    state.hypotheses.forEach((hyp, idx) => {
      const mark = hyp.outcome === 'success' ? '🟢' : '🔴';
      console.log(`\x1b[1m\x1b[35m│\x1b[0m   [Attempt #${idx + 1}] Error Signature: "${hyp.error_signature}"`);
      console.log(`\x1b[1m\x1b[35m│\x1b[0m     💡 Diagnosis: ${hyp.diagnosis}`);
      console.log(`\x1b[1m\x1b[35m│\x1b[0m     🔧 Fix Action: ${hyp.fix_attempted} -> (${mark} ${hyp.outcome.toUpperCase()})`);
    });
  } else {
    console.log('\x1b[1m\x1b[35m│\x1b[0m   🟢 No failures encountered! Safe execution and verification passed first try.');
  }
  
  console.log('\x1b[1m\x1b[35m└──────────────────────────────────────────────────────────────────┘\x1b[0m\n');
}
