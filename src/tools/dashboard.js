export const dashboardHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Forge Developer Dashboard</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg-color: #0b0f19;
      --card-bg: rgba(17, 24, 39, 0.7);
      --border-color: rgba(255, 255, 255, 0.08);
      --primary-gradient: linear-gradient(135deg, #3b82f6, #8b5cf6);
      --accent-cyan: #06b6d4;
      --accent-green: #10b981;
      --accent-yellow: #f59e0b;
      --accent-red: #ef4444;
      --text-main: #f3f4f6;
      --text-muted: #9ca3af;
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    body {
      background-color: var(--bg-color);
      background-image: 
        radial-gradient(at 0% 0%, rgba(59, 130, 246, 0.15) 0px, transparent 50%),
        radial-gradient(at 100% 100%, rgba(139, 92, 246, 0.15) 0px, transparent 50%);
      color: var(--text-main);
      font-family: 'Inter', sans-serif;
      min-height: 100vh;
      overflow-x: hidden;
    }

    header {
      backdrop-filter: blur(12px);
      background: rgba(15, 23, 42, 0.6);
      border-bottom: 1px solid var(--border-color);
      padding: 1.25rem 2rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .brand-logo {
      background: var(--primary-gradient);
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 0.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      color: white;
      box-shadow: 0 0 15px rgba(59, 130, 246, 0.5);
    }

    .brand-title {
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: -0.025em;
      background: linear-gradient(to right, #ffffff, #9ca3af);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .badge {
      background: rgba(16, 185, 129, 0.1);
      border: 1px solid rgba(16, 185, 129, 0.2);
      color: var(--accent-green);
      padding: 0.35rem 0.75rem;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .badge-dot {
      width: 6px;
      height: 6px;
      background-color: var(--accent-green);
      border-radius: 50%;
      animation: pulse 1.5s infinite;
    }

    main {
      max-width: 1400px;
      margin: 2rem auto;
      padding: 0 1.5rem;
      display: grid;
      grid-template-columns: 1fr 1.2fr;
      gap: 1.5rem;
    }

    @media (max-width: 1024px) {
      main {
        grid-template-columns: 1fr;
      }
    }

    .card {
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      border: 1px solid var(--border-color);
      border-radius: 1rem;
      padding: 1.5rem;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
      margin-bottom: 1.5rem;
      transition: all 0.3s ease;
    }

    .card:hover {
      border-color: rgba(255, 255, 255, 0.15);
      box-shadow: 0 15px 35px rgba(0, 0, 0, 0.3);
    }

    .card-title {
      font-size: 1.1rem;
      font-weight: 600;
      margin-bottom: 1.25rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 0.75rem;
    }

    .objective-header {
      background: var(--primary-gradient);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      font-weight: 700;
    }

    /* Task Flow Styling */
    .task-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .task-item {
      border: 1px solid var(--border-color);
      background: rgba(255, 255, 255, 0.02);
      border-radius: 0.75rem;
      padding: 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      transition: all 0.3s ease;
    }

    .task-item.status-done {
      border-left: 4px solid var(--accent-green);
      background: rgba(16, 185, 129, 0.03);
    }

    .task-item.status-working {
      border-left: 4px solid var(--accent-yellow);
      background: rgba(245, 158, 11, 0.03);
      animation: pulse-border 2s infinite;
    }

    .task-item.status-failed {
      border-left: 4px solid var(--accent-red);
      background: rgba(239, 68, 68, 0.03);
    }

    .task-item.status-pending {
      border-left: 4px solid #4b5563;
      opacity: 0.6;
    }

    .task-info {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .task-desc {
      font-weight: 500;
      font-size: 0.95rem;
    }

    .task-deps {
      font-size: 0.75rem;
      color: var(--text-muted);
    }

    .task-status {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      padding: 0.25rem 0.5rem;
      border-radius: 0.25rem;
    }

    .status-badge-done { background: rgba(16, 185, 129, 0.15); color: var(--accent-green); }
    .status-badge-working { background: rgba(245, 158, 11, 0.15); color: var(--accent-yellow); }
    .status-badge-failed { background: rgba(239, 68, 68, 0.15); color: var(--accent-red); }
    .status-badge-pending { background: rgba(255, 255, 255, 0.05); color: var(--text-muted); }

    /* Hacker Terminal */
    .terminal-box {
      background: #05070c;
      border: 1px solid var(--border-color);
      border-radius: 0.75rem;
      font-family: 'Fira Code', monospace;
      font-size: 0.85rem;
      padding: 1.25rem;
      height: 350px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      box-shadow: inset 0 0 20px rgba(0,0,0,0.8);
    }

    .terminal-line {
      display: flex;
      gap: 0.5rem;
      line-height: 1.4;
    }

    .terminal-time {
      color: #3b82f6;
      flex-shrink: 0;
    }

    .terminal-content {
      color: #a7f3d0;
      word-break: break-all;
    }

    /* Ledger Files */
    .file-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 1rem;
    }

    .file-card {
      border: 1px solid var(--border-color);
      background: rgba(255, 255, 255, 0.01);
      border-radius: 0.5rem;
      padding: 0.75rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .file-icon {
      font-size: 1.25rem;
    }

    .file-name {
      font-size: 0.85rem;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Hypotheses Ledger */
    .hyp-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .hyp-card {
      border: 1px solid var(--border-color);
      border-radius: 0.5rem;
      padding: 0.85rem;
      background: rgba(255, 255, 255, 0.01);
    }

    .hyp-sig {
      font-family: 'Fira Code', monospace;
      font-size: 0.8rem;
      color: var(--accent-red);
      font-weight: 600;
      margin-bottom: 0.35rem;
    }

    .hyp-diagnosis {
      font-size: 0.8rem;
      color: var(--text-muted);
      line-height: 1.4;
      margin-bottom: 0.5rem;
    }

    .hyp-footer {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
    }

    /* Animations */
    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.5; transform: scale(0.9); }
    }

    @keyframes pulse-border {
      0%, 100% { border-color: var(--accent-yellow); }
      50% { border-color: rgba(245, 158, 11, 0.2); }
    }
  </style>
</head>
<body>

  <header>
    <div class="brand">
      <div class="brand-logo">F</div>
      <div class="brand-title">Forge Local Agent</div>
    </div>
    <div class="badge">
      <div class="badge-dot"></div>
      OFFLINE SECURE GATEWAY
    </div>
  </header>

  <main>
    <div>
      <!-- Active Objective -->
      <div class="card">
        <div class="card-title">🎯 ACTIVE OBJECTIVE</div>
        <h2 id="objective-text" class="objective-header">Scanning local environment...</h2>
      </div>

      <!-- Task flow -->
      <div class="card">
        <div class="card-title">⚙️ TASK EXECUTION PIPELINE</div>
        <div id="task-container" class="task-list">
          <p style="color: var(--text-muted); font-size: 0.9rem;">Initializing task plan graph...</p>
        </div>
      </div>

      <!-- File Ledger -->
      <div class="card">
        <div class="card-title">📂 WORKSPACE LEDGER</div>
        <div id="file-container" class="file-grid">
          <p style="color: var(--text-muted); font-size: 0.9rem;">No files registered yet.</p>
        </div>
      </div>
    </div>

    <div>
      <!-- Hacker Console Logs -->
      <div class="card">
        <div class="card-title">💻 REAL-TIME ENGINE LOGS</div>
        <div id="terminal" class="terminal-box">
          <div class="terminal-line">
            <span class="terminal-time">[SYSTEM]</span>
            <span class="terminal-content">Awaiting local state synchronization...</span>
          </div>
        </div>
      </div>

      <!-- Failure Diagnosis Hypotheses -->
      <div class="card">
        <div class="card-title">🧠 COGNITIVE RECOVERY LEDGER</div>
        <div id="hyp-container" class="hyp-list">
          <p style="color: var(--text-muted); font-size: 0.9rem;">No failure diagnostics required yet.</p>
        </div>
      </div>
    </div>
  </main>

  <script>
    async function updateDashboard() {
      try {
        const response = await fetch('/api/state');
        if (!response.ok) return;
        const state = await response.json();

        // 1. Objective
        document.getElementById('objective-text').innerText = state.objective || 'No active project';

        // 2. Tasks
        const taskContainer = document.getElementById('task-container');
        if (state.plan && state.plan.length > 0) {
          taskContainer.innerHTML = state.plan.map(task => {
            const statusClass = task.status === 'done' ? 'status-done' :
                                task.status === 'in_progress' ? 'status-working' :
                                task.status === 'failed' ? 'status-failed' : 'status-pending';
            const badgeClass = task.status === 'done' ? 'status-badge-done' :
                               task.status === 'in_progress' ? 'status-badge-working' :
                               task.status === 'failed' ? 'status-badge-failed' : 'status-badge-pending';
            const statusLabel = task.status === 'done' ? 'DONE' :
                                task.status === 'in_progress' ? 'WORKING' :
                                task.status === 'failed' ? 'FAILED' : 'PENDING';
            const depsLabel = task.depends_on.length > 0 ? 'Depends on: ' + task.depends_on.join(', ') : 'Root task';
            
            return \`
              <div class="task-item \${statusClass}">
                <div class="task-info">
                  <div class="task-desc">\${task.description}</div>
                  <div class="task-deps">\${depsLabel}</div>
                </div>
                <div class="task-status \${badgeClass}">\${statusLabel}</div>
              </div>
            \`;
          }).join('');
        } else {
          taskContainer.innerHTML = '<p style="color: var(--text-muted); font-size: 0.9rem;">No active task graph compiled.</p>';
        }

        // 3. File Ledger
        const fileContainer = document.getElementById('file-container');
        const files = Object.keys(state.file_ledger || {});
        if (files.length > 0) {
          fileContainer.innerHTML = files.map(file => {
            let icon = '📄';
            if (file.endsWith('.html')) icon = '🌐';
            if (file.endsWith('.css')) icon = '🎨';
            if (file.endsWith('.json')) icon = '⚙️';
            if (file.endsWith('.js')) icon = '⚡';
            
            const baseName = file.replace('dist/', '');
            return \`
              <div class="file-card">
                <span class="file-icon">\${icon}</span>
                <span class="file-name" title="\${file}">\${baseName}</span>
              </div>
            \`;
          }).join('');
        } else {
          fileContainer.innerHTML = '<p style="color: var(--text-muted); font-size: 0.9rem;">No files modified in dist/ workspace yet.</p>';
        }

        // 4. Session Logs
        const terminal = document.getElementById('terminal');
        if (state.session_log && state.session_log.length > 0) {
          terminal.innerHTML = state.session_log.map((log, index) => {
            let logColor = '#a7f3d0'; // default mint
            if (log.includes('[Sense]')) logColor = '#67e8f9'; // cyan
            if (log.includes('[Decide]')) logColor = '#fde047'; // yellow
            if (log.includes('[Act]')) logColor = '#86efac'; // green
            if (log.includes('[Check]')) logColor = '#f472b6'; // magenta
            if (log.includes('[Failure]')) logColor = '#fca5a5'; // red
            
            return \`
              <div class="terminal-line" style="color: \${logColor}">
                <span class="terminal-time">[LOG \${index + 1}]</span>
                <span class="terminal-content">\${log}</span>
              </div>
            \`;
          }).join('');
          // Auto scroll to bottom
          terminal.scrollTop = terminal.scrollHeight;
        }

        // 5. Hypotheses
        const hypContainer = document.getElementById('hyp-container');
        if (state.hypotheses && state.hypotheses.length > 0) {
          hypContainer.innerHTML = state.hypotheses.map(hyp => {
            const outcomeColor = hyp.outcome === 'success' ? 'var(--accent-green)' :
                                 hyp.outcome === 'failed' ? 'var(--accent-red)' : 'var(--accent-yellow)';
            return \`
              <div class="hyp-card">
                <div class="hyp-sig">\${hyp.error_signature}</div>
                <div class="hyp-diagnosis">💡 <strong>Diagnosis:</strong> \${hyp.diagnosis}</div>
                <div class="hyp-footer">
                  <span style="color: var(--text-muted)">Fix: \${hyp.fix_attempted}</span>
                  <span style="color: \${outcomeColor}; font-weight: 600; text-transform: uppercase;">\${hyp.outcome}</span>
                </div>
              </div>
            \`;
          }).join('');
        } else {
          hypContainer.innerHTML = '<p style="color: var(--text-muted); font-size: 0.9rem;">No failures encountered yet. Safe execution in progress.</p>';
        }

      } catch (err) {
        console.error('Error fetching state:', err);
      }
    }

    // Poll every 1 second
    setInterval(updateDashboard, 1000);
    updateDashboard();
  </script>
</body>
</html>
`;
