import { infer } from '../src/router.js';

async function run() {
  console.log('--- Starting Milestone 1 Verification ---');

  const useMock = process.argv.includes('--mock');
  if (useMock) {
    console.log('Running in MOCK mode...');
  } else {
    console.log('Attempting connection to live GPU inference endpoint at localhost:8080...');
  }

  // 1. E4B (Quality Path) for Planner
  const plannerMessages = [
    { role: 'user', content: 'Create a plan to build a standard unit converter PWA app.' }
  ];

  console.log('\n[1/2] Fetching E4B (Quality Path)...');
  try {
    const start = Date.now();
    const result = await infer({
      role: 'planner',
      messages: plannerMessages,
      expect: 'json',
      mock: useMock
    });
    const latency = Date.now() - start;
    console.log(`E4B Result Successful! Latency: ${latency}ms`);
    console.log('Decided Tasks:', JSON.stringify(result, null, 2));
  } catch (err) {
    console.error('E4B Call Failed:', err.message);
    if (!useMock) {
      console.log('Live inference endpoint not running or refused connection. Try running with: node test/test-inference.js --mock');
    }
  }

  // 2. E2B (Fast Path) for Judge
  const judgeMessages = [
    { role: 'user', content: 'Evaluate this build output: "Build Succeeded. All 12/12 audits passed." Is this successful?' }
  ];

  console.log('\n[2/2] Fetching E2B (Fast Path)...');
  try {
    const start = Date.now();
    const result = await infer({
      role: 'judge',
      messages: judgeMessages,
      expect: 'json',
      mock: useMock
    });
    const latency = Date.now() - start;
    console.log(`E2B Result Successful! Latency: ${latency}ms`);
    console.log('Judge Verdict:', JSON.stringify(result, null, 2));
  } catch (err) {
    console.error('E2B Call Failed:', err.message);
  }

  console.log('\n--- Verification Finished ---');
}

run().catch(console.error);
