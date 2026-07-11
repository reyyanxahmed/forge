#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { loadState } from '../src/state.js';
import { runLoop } from '../src/loop.js';
import { printTaskGraph } from '../src/trace.js';

const [,, command, ...args] = process.argv;

const MOCK_FLAG = process.argv.includes('--mock');
const DEMO_FLAG = process.argv.includes('--demo');

function showHelp() {
  console.log(`
Forge - Autonomous On-Device PWA Coding Agent
Usage:
  forge new "<natural language request>" [--mock] [--demo]
  forge resume [--mock] [--demo]
  forge status
`);
  process.exit(1);
}

async function main() {
  if (!command) {
    showHelp();
  }

  const projDir = process.cwd();

  switch (command.toLowerCase()) {
    case 'status': {
      const state = loadState(projDir);
      if (!state) {
        console.log('no active project');
        process.exit(0);
      }
      printTaskGraph(state);
      break;
    }

    case 'new': {
      // Find objective from args (excluding flags)
      const cleanArgs = args.filter(arg => !arg.startsWith('--'));
      const objective = cleanArgs[0];
      if (!objective) {
        console.error('Error: Please provide an app request description, e.g., forge new "tip calculator"');
        process.exit(1);
      }
      
      await runLoop({
        objective,
        isNew: true,
        projDir,
        mock: MOCK_FLAG,
        demo: DEMO_FLAG
      });
      break;
    }

    case 'resume': {
      await runLoop({
        isNew: false,
        projDir,
        mock: MOCK_FLAG,
        demo: DEMO_FLAG
      });
      break;
    }

    default:
      showHelp();
  }
}

main().catch(err => {
  console.error('Unhandled execution error:', err.message);
  process.exit(1);
});
