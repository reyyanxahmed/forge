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
}

export function traceDecide(model, msg) {
  checkDemoDelay();
  console.log(`\x1b[33m[Decide] [${model}]\x1b[0m ${msg}`);
}

export function traceAct(msg) {
  checkDemoDelay();
  console.log(`\x1b[32m[Act]\x1b[0m ${msg}`);
}

export function traceCheck(msg) {
  checkDemoDelay();
  console.log(`\x1b[35m[Check]\x1b[0m ${msg}`);
}

export function traceError(msg) {
  checkDemoDelay();
  console.log(`\x1b[31m[Failure]\x1b[0m ${msg}`);
}

export function traceEscalation(msg) {
  checkDemoDelay();
  console.log(`\x1b[1m\x1b[37m[Escalation]\x1b[0m ${msg}`);
}
