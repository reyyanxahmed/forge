import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

export async function startServer(projDir, port = 3000) {
  const distDir = path.join(projDir, 'dist');
  
  const server = http.createServer((req, res) => {
    let reqPath = req.url.split('?')[0];
    if (reqPath === '/') reqPath = '/index.html';
    
    const targetFile = path.join(distDir, reqPath);
    
    // Ensure path jailing inside dist folder
    const resolvedDist = path.resolve(distDir);
    const resolvedTarget = path.resolve(targetFile);
    
    if (!resolvedTarget.startsWith(resolvedDist)) {
      res.statusCode = 403;
      res.end('Access Denied');
      return;
    }
    
    if (!fs.existsSync(resolvedTarget) || fs.statSync(resolvedTarget).isDirectory()) {
      res.statusCode = 404;
      res.end('File Not Found');
      return;
    }
    
    const ext = path.extname(resolvedTarget).toLowerCase();
    const mimeTypes = {
      '.html': 'text/html',
      '.css': 'text/css',
      '.js': 'application/javascript',
      '.json': 'application/json',
      '.png': 'image/png',
      '.ico': 'image/x-icon',
      '.svg': 'image/svg+xml'
    };
    
    res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
    fs.createReadStream(resolvedTarget).pipe(res);
  });
  
  return new Promise((resolve) => {
    server.listen(port, '0.0.0.0', () => {
      resolve(server);
    });
  });
}

export function validatePWA(projDir) {
  const distDir = path.join(projDir, 'dist');
  const checklist = [
    { name: 'manifest_present', label: 'manifest.json present in dist/', status: false },
    { name: 'manifest_valid_json', label: 'manifest.json is valid JSON', status: false },
    { name: 'manifest_required_fields', label: 'manifest contains required PWA metadata', status: false },
    { name: 'service_worker_present', label: 'sw.js present in dist/', status: false },
    { name: 'service_worker_registered', label: 'sw.js registered in index.html', status: false },
    { name: 'icons_resolvable', label: 'PWA icons present in dist/', status: false }
  ];
  
  // 1. Manifest Audit
  const manifestPath = path.join(distDir, 'manifest.json');
  let manifestData = null;
  if (fs.existsSync(manifestPath)) {
    checklist[0].status = true;
    try {
      const raw = fs.readFileSync(manifestPath, 'utf8');
      manifestData = JSON.parse(raw);
      checklist[1].status = true;
      
      const hasFields = manifestData.name && manifestData.short_name && manifestData.start_url && manifestData.display;
      if (hasFields) {
        checklist[2].status = true;
      }
    } catch (_) {}
  }
  
  // 2. Service Worker Audit
  const swPath = path.join(distDir, 'sw.js');
  if (fs.existsSync(swPath)) {
    checklist[3].status = true;
  }
  
  const indexPath = path.join(distDir, 'index.html');
  if (fs.existsSync(indexPath)) {
    const html = fs.readFileSync(indexPath, 'utf8');
    if (html.includes('serviceWorker.register') || html.includes('sw.js')) {
      checklist[4].status = true;
    }
  }
  
  // 3. Icons Audit
  let iconsResolved = false;
  if (manifestData && Array.isArray(manifestData.icons) && manifestData.icons.length > 0) {
    let allIconsPresent = true;
    for (const icon of manifestData.icons) {
      const iconPath = path.join(distDir, icon.src);
      if (!fs.existsSync(iconPath)) {
        allIconsPresent = false;
        break;
      }
    }
    if (allIconsPresent) {
      iconsResolved = true;
    }
  } else {
    // Check fallback icons
    if (fs.existsSync(path.join(distDir, 'icon.png')) || fs.existsSync(path.join(distDir, 'icon.ico'))) {
      iconsResolved = true;
    }
  }
  checklist[5].status = iconsResolved;
  
  const isValid = checklist.every(item => item.status === true);
  return {
    isValid,
    checklist
  };
}
