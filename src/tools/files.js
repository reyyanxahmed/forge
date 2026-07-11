import fs from 'node:fs';
import path from 'node:path';

function jailPath(projDir, relPath) {
  const resolvedProj = path.resolve(projDir);
  const resolvedTarget = path.resolve(resolvedProj, relPath);
  if (!resolvedTarget.startsWith(resolvedProj)) {
    throw new Error(`Path traversal rejected: ${relPath} escapes project root`);
  }
  return resolvedTarget;
}

export function readFile(projDir, relPath) {
  const target = jailPath(projDir, relPath);
  if (!fs.existsSync(target)) {
    throw new Error(`File does not exist: ${relPath}`);
  }
  return fs.readFileSync(target, 'utf8');
}

export function writeFile(projDir, relPath, content) {
  const target = jailPath(projDir, relPath);
  const dir = path.dirname(target);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  fs.writeFileSync(target, content, 'utf8');
}

export function listFiles(projDir, relDir = '') {
  const baseTarget = jailPath(projDir, relDir);
  const results = [];

  const ignoreList = ['.git', '.forge', 'node_modules'];

  function walk(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (ignoreList.includes(entry.name)) continue;

      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else {
        const rel = path.relative(projDir, fullPath);
        results.push(rel);
      }
    }
  }

  if (fs.existsSync(baseTarget)) {
    walk(baseTarget);
  }
  return results;
}
