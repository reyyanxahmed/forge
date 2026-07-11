#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { loadState } from '../src/state.js';
import { runLoop } from '../src/loop.js';
import { printTaskGraph } from '../src/trace.js';
import { enableVoice } from '../src/tools/speaker.js';

const [,, command, ...args] = process.argv;

const MOCK_FLAG = process.argv.includes('--mock');
const DEMO_FLAG = process.argv.includes('--demo');
const VOICE_FLAG = process.argv.includes('--voice');

let SKETCH_PATH = null;
const sketchIndex = process.argv.indexOf('--sketch');
if (sketchIndex !== -1 && sketchIndex + 1 < process.argv.length) {
  SKETCH_PATH = process.argv[sketchIndex + 1];
}

function showHelp() {
  console.log(`
Forge - Autonomous On-Device Native Android Coding Agent
Usage:
  forge new "<natural language request>" [--mock] [--demo] [--voice] [--sketch <path_to_sketch>]
  forge resume [--mock] [--demo] [--voice]
  forge status
  forge explain
`);
  process.exit(1);
}

async function main() {
  if (!command) {
    showHelp();
  }

  const projDir = process.cwd();

  if (VOICE_FLAG || DEMO_FLAG) {
    enableVoice(true);
  }

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

    case 'explain': {
      const state = loadState(projDir);
      if (!state) {
        console.log('No active project found.');
        process.exit(0);
      }
      const { explainTimeline } = await import('../src/trace.js');
      explainTimeline(state);
      break;
    }

    case 'new': {
      // Find objective from args (excluding flags)
      const cleanArgs = [];
      for (let i = 0; i < args.length; i++) {
        if (args[i].startsWith('--')) {
          if (args[i] === '--sketch') {
            i++; // skip the path that follows
          }
          continue;
        }
        cleanArgs.push(args[i]);
      }
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
        demo: DEMO_FLAG,
        voice: VOICE_FLAG,
        sketchPath: SKETCH_PATH
      });
      break;
    }

    case 'resume': {
      await runLoop({
        isNew: false,
        projDir,
        mock: MOCK_FLAG,
        demo: DEMO_FLAG,
        voice: VOICE_FLAG
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
