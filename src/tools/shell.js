import { exec } from 'node:child_process';
import { promisify } from 'node:util';

const execAsync = promisify(exec);

const ALLOWED_COMMANDS = ['node', 'npx'];

function truncateOutput(text) {
  if (!text) return '';
  const lines = text.split('\n');
  if (lines.length <= 200) {
    return text;
  }
  const firstPart = lines.slice(0, 150);
  const lastPart = lines.slice(lines.length - 50);
  return [
    ...firstPart,
    `... [truncated ${lines.length - 200} lines] ...`,
    ...lastPart
  ].join('\n');
}

export function normalizeErrorSignature(stderr) {
  if (!stderr) return '';
  let sig = stderr;

  // 1. Replace absolute paths
  sig = sig.replace(/\/[^:\s]+/g, '[PATH]');
  
  // 2. Replace line/column numbers
  sig = sig.replace(/:[0-9]+:[0-9]+/g, ':[LINE]:[COL]');
  sig = sig.replace(/:[0-9]+/g, ':[LINE]');
  
  // 3. Replace hex addresses
  sig = sig.replace(/0x[0-9a-fA-F]+/g, '[HEX]');

  // 4. Extract core error messages (filter out standard stack trace lines starting with 'at')
  const lines = sig.split('\n')
    .map(line => line.trim())
    .filter(line => {
      if (line.startsWith('at ')) return false;
      return line.includes('Error') || line.includes('Exception') || line.length > 0;
    });

  // Extract first 3 structural error lines as the signature
  return lines.slice(0, 3).join(' | ');
}

export async function execCommand(command, cwd) {
  // Validate allowlist
  const baseCmd = command.trim().split(/\s+/)[0];
  if (!ALLOWED_COMMANDS.includes(baseCmd)) {
    throw new Error(`Command blocked: Execution of '${baseCmd}' is not in allowlist.`);
  }

  try {
    // Run command within current workspace directory
    const { stdout, stderr } = await execAsync(command, { cwd, timeout: 30000 });
    
    return {
      code: 0,
      stdout: truncateOutput(stdout),
      stderr: truncateOutput(stderr),
      signature: ''
    };
  } catch (err) {
    const code = err.code ?? 1;
    const stdout = truncateOutput(err.stdout ?? '');
    const stderr = truncateOutput(err.stderr ?? err.message ?? '');
    const signature = normalizeErrorSignature(stderr);

    return {
      code,
      stdout,
      stderr,
      signature
    };
  }
}
