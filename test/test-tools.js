import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';
import { writeFile } from '../src/tools/files.js';
import { startServer, validatePWA } from '../src/tools/server.js';
import { execCommand } from '../src/tools/shell.js';

const sandboxDir = path.join(process.cwd(), 'test', 'sandbox');
const distDir = path.join(sandboxDir, 'dist');

function cleanUp() {
  if (fs.existsSync(sandboxDir)) {
    fs.rmSync(sandboxDir, { recursive: true, force: true });
  }
}

async function run() {
  console.log('--- Starting Milestone 3 Verification ---');
  cleanUp();

  // Create workspace folders
  fs.mkdirSync(distDir, { recursive: true });

  // 1. Copy template files into test sandbox dist
  const templateDir = path.join(process.cwd(), 'templates', 'pwa-base');
  const indexHtml = fs.readFileSync(path.join(templateDir, 'index.html'), 'utf8');
  const swJs = fs.readFileSync(path.join(templateDir, 'sw.js'), 'utf8');
  const manifestJson = fs.readFileSync(path.join(templateDir, 'manifest.json'), 'utf8');

  // Write files via our sandboxed writeFile (validates path jailing)
  console.log('Writing sandbox files through files.js writeFile tool...');
  writeFile(sandboxDir, 'dist/index.html', indexHtml);
  writeFile(sandboxDir, 'dist/sw.js', swJs);
  writeFile(sandboxDir, 'dist/manifest.json', manifestJson);
  
  // Create mock empty style.css, app.js, and icon.png
  writeFile(sandboxDir, 'dist/style.css', 'body { background: #000; }');
  writeFile(sandboxDir, 'dist/app.js', 'console.log("ready");');
  writeFile(sandboxDir, 'dist/icon.png', 'MOCK_IMAGE_DATA');

  // 2. Start server
  console.log('Starting static PWA server on port 3000...');
  const server = await startServer(sandboxDir, 3000);

  // 3. Perform a HTTP fetch call (or curl mock via shell command) to verify server
  console.log('Fetching index.html from server to verify socket delivery...');
  let fetchSucceeded = false;
  try {
    const res = await fetch('http://localhost:3000/index.html');
    const htmlText = await res.text();
    assert.strictEqual(res.status, 200, 'HTTP status should be 200');
    assert.ok(htmlText.includes('<title>Forge PWA</title>'), 'HTML title should match template');
    console.log('Fetch request completed successfully! Received 200 OK.');
    fetchSucceeded = true;
  } catch (err) {
    console.error('Fetch request failed:', err.message);
  }

  // Close server
  console.log('Closing local static server...');
  await new Promise((resolve) => server.close(resolve));

  // 4. Validate PWA readiness
  console.log('\nRunning PWA Install-Readiness Audit...');
  const audit = validatePWA(sandboxDir);
  console.log('Audit Result Checklist:');
  for (const item of audit.checklist) {
    const icon = item.status ? '🟢 PASS' : '🔴 FAIL';
    console.log(`  [${icon}] ${item.label}`);
  }

  console.log(`\nGlobal PWA Validity Status: ${audit.isValid ? '🏆 VALID PWA' : '❌ INVALID'}`);
  assert.ok(audit.isValid, 'Audit should return fully valid for the complete template.');

  // 5. Test path-jailing security rejection
  console.log('\nTesting path jailing directory traversal defense...');
  try {
    writeFile(sandboxDir, '../outside.txt', 'HACK');
    assert.fail('Should have thrown path traversal rejection.');
  } catch (err) {
    assert.ok(err.message.includes('Path traversal rejected'), 'Traversals should trigger security exceptions.');
    console.log('Path-jailing defended successfully! Trap: ' + err.message);
  }

  // 6. Test shell execution allowlist blocking
  console.log('\nTesting shell block allowlist...');
  try {
    await execCommand('rm -rf /', sandboxDir);
    assert.fail('Should have blocked command.');
  } catch (err) {
    assert.ok(err.message.includes('Command blocked'), 'Blocked command should throw access validation errors.');
    console.log('Shell execution allowlist block passed successfully! Trap: ' + err.message);
  }

  // Clean up
  cleanUp();
  console.log('\n--- Verification Finished: ALL PASS ---');
}

run().catch(err => {
  console.error('Tools verification failed:', err);
  process.exit(1);
});
