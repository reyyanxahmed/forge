# Forge 🛠️
> **Autonomous On-Device PWA Coding Agent Powered by Gemma 4**

Forge is an offline, autonomous, agentic coding assistant running entirely on a Google Pixel 10 Pro. Give Forge a natural language goal like `"build a budget splitting app"`, and it plans, generates code, audits, runs diagnostics, and recovers from errors autonomously.

---

## 🏗️ System Architecture

```
                    +------------------------------------+
                    |        USER APPS REQUEST           |
                    +-----------------+------------------+
                                      |
                                      v
                             +--------+--------+
                             |   CLI (forge)   |
                             +--------+--------+
                                      |
                                      v
                             +--------+--------+
       +-------------------->|  AGENT LOOP     |<--------------------+
       |                     +--------+--------+                     |
       |                              |                              |
       |                              v                              |
    [SENSE]                    +------+------+                    [CHECK]
Loads active state,            |   ROUTER    |               Runs local static server,
context, and error             +------+------+               audits PWA compliance, and
histories.                            |                      parses stderr logs.
       ^                              |                              ^
       |               +--------------+--------------+               |
       |               |                             |               |
       |               v                             v               |
       |        [GEMMA 4 E4B]                 [GEMMA 4 E2B]          |
       |        (Quality Path)                (Fast Path)            |
       |        * Planning                    * Binary Decisions     |
       |        * Code Generation             * Task Completions     |
       |        * Fix Synthesis               * Success Grading      |
       |               |                             |               |
       |               +--------------+--------------+               |
       |                              |                              |
       |                              v                              |
       |                           [ACT]                             |
       |                  Executing File Modifications               |
       |                  & Allowlisted Shell Tasks                  |
       |                              |                              |
       +------------------------------+------------------------------+
```

---

## 📊 Judging Criteria Mapping Table

| Judging Criterion (from Brief) | Forge Architecture Implementation |
| :--- | :--- |
| **"Real agency means holding state across a task..."** | **Fully Persisted State Machine:** All agent state, task graph, and error hypotheses are written atomically to `.forge/state.json` inside the project after every iteration. |
| **"...deciding what to do next based on what's already been learned..."** | **Hypotheses Registry:** Forge registers normalized error signatures and attempted fixes. Before generating a new fix, the Gemma-4-E4B model reviews prior failures to guarantee no repetitive, broken repairs. |
| **"...recovering when the plan breaks, entirely offline."** | **Autonomic Fixer Loop:** When a build or lint test fails, the judge (E2B) flags the issue, the diagnosis engine (E4B) creates a hypothesis, writes the corrected code, and re-audits. |
| **"...entirely offline... build the full sense-decide-act-check loop."** | **Self-Contained VM Execution:** Runs locally inside Debian VM on node standard library. Inference connects to local Gemma GPU endpoint on `localhost:8080`. No cloud dependencies. |
| **Two-Model Routing Optimization** | **Policy Router:** Allocates Gemma-4-E4B for high-cognition tasks and Gemma-4-E2B for classification tasks, logging model, token size, and latency live in the reasoning trace. |

---

## 🚀 Quick Start

```bash
# Start a new Progressive Web App project
forge new "build a splitwise app"

# Resume the most recently killed process from state.json
forge resume

# View current task status graph
forge status
```
