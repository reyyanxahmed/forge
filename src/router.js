import fs from 'node:fs';
import path from 'node:path';
import { traceDecide, traceError } from './trace.js';

// Route policy mapping
const ROLE_TO_MODEL = {
  planner: 'gemma-4-e4b',
  coder: 'gemma-4-e4b',
  fixer: 'gemma-4-e4b',
  judge: 'gemma-4-e2b'
};

function estimateTokens(text) {
  if (typeof text !== 'string') return 0;
  return Math.ceil(text.length / 4);
}

function cleanJson(str) {
  let cleaned = str.trim();
  // Strip markdown blocks if any
  cleaned = cleaned.replace(/^```(?:json)?\s*/i, '');
  cleaned = cleaned.replace(/\s*```$/, '');
  return cleaned.trim();
}

// Loads canned responses for development and unit testing
function getMockResponse(role, messages) {
  const lastMsg = messages[messages.length - 1]?.content || '';
  const fixturesDir = path.join(process.cwd(), 'test', 'fixtures');
  
  if (!fs.existsSync(fixturesDir)) {
    fs.mkdirSync(fixturesDir, { recursive: true });
  }

  const roleLower = role.toLowerCase();

  if (roleLower === 'planner') {
    const file = path.join(fixturesDir, 'planner.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    // Default fallback
    return {
      tasks: [
        { id: "1", description: "Initialize workspace with index.html, app.js, style.css and manifest.json", depends_on: [] },
        { id: "2", description: "Write app logic and layout in files", depends_on: ["1"] },
        { id: "3", description: "Verify PWA compliance and serve", depends_on: ["2"] }
      ]
    };
  }

  if (roleLower === 'coder') {
    // Determine which task we are coder for
    if (lastMsg.includes('Initialize') || lastMsg.includes('task 1') || lastMsg.includes('"id": "1"')) {
      const file = path.join(fixturesDir, 'coder_task1.json');
      if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
      return {
        operations: [
          { op: "write", path: "dist/manifest.json", content: '{\n  "name": "Mock Split App",\n  "short_name": "SplitApp",\n  "start_url": "index.html",\n  "display": "standalone",\n  "background_color": "#121212",\n  "theme_color": "#121212",\n  "icons": [{ "src": "icon.png", "sizes": "192x192", "type": "image/png" }]\n}' },
          { op: "write", path: "dist/sw.js", content: 'self.addEventListener("fetch", () => {});' },
          { op: "write", path: "dist/index.html", content: '<!DOCTYPE html><html><head><link rel="manifest" href="manifest.json"><script src="sw.js"></script></head><body><div id="app">Hello Mock</div></body></html>' }
        ]
      };
    }
    
    // For other tasks
    const file = path.join(fixturesDir, 'coder_task2.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    return {
      operations: [
        { op: "write", path: "dist/app.js", content: 'console.log("Mock App Loaded");' }
      ]
    };
  }

  if (roleLower === 'fixer') {
    const file = path.join(fixturesDir, 'fixer.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    return {
      explanation: "Fixed missing closing bracket",
      operations: [
        { op: "write", path: "dist/app.js", content: 'console.log("Mock App Loaded (Fixed)");' }
      ]
    };
  }

  if (roleLower === 'judge') {
    // E2B binary decisions. Check last command / output
    const file = path.join(fixturesDir, 'judge.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    
    // Check if the command indicates an error we want to fail
    if (lastMsg.includes('FAIL') || lastMsg.includes('error') || lastMsg.includes('sabotage')) {
      return { verdict: "fail", reason: "Found runtime script error or syntax breakdown in logs" };
    }
    return { verdict: "pass", reason: "Static analysis and file validation succeeded cleanly" };
  }

  return {};
}

export async function infer({ role, messages, expect, mock = false }) {
  const model = ROLE_TO_MODEL[role] || 'gemma-4-e2b';
  const start = Date.now();

  if (mock) {
    // Simulated response timing
    const elapsed = 50 + Math.floor(Math.random() * 80);
    const mockRes = getMockResponse(role, messages);
    const mockString = JSON.stringify(mockRes);
    const tokens = estimateTokens(JSON.stringify(messages) + mockString);
    
    traceDecide(model, `[MOCK] Served role: ${role} | Latency: ${elapsed}ms | Est. Tokens: ${tokens}`);
    return expect === 'json' ? mockRes : mockString;
  }

  const endpoint = 'http://localhost:8080/v1/chat/completions';
  
  const payload = {
    model,
    messages,
    temperature: 0.1, // low temp for structured adherence
    response_format: expect === 'json' ? { type: 'json_object' } : undefined
  };

  let responseText = '';
  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      throw new Error(`Inference HTTP error! status: ${res.status}`);
    }

    const data = await res.json();
    responseText = data.choices?.[0]?.message?.content || '';
  } catch (err) {
    traceError(`Inference failed on ${model}: ${err.message}`);
    throw err;
  }

  const elapsed = Date.now() - start;
  const inTokens = estimateTokens(JSON.stringify(messages));
  const outTokens = estimateTokens(responseText);
  
  traceDecide(model, `Served role: ${role} | Latency: ${elapsed}ms | Tokens: In~${inTokens} Out~${outTokens}`);

  if (expect === 'json') {
    try {
      const cleaned = cleanJson(responseText);
      return JSON.parse(cleaned);
    } catch (parseErr) {
      traceError(`JSON Parse error on ${model}. Attempting self-correction retry...`);
      
      // Self-correction conversation append
      const retryMessages = [
        ...messages,
        { role: 'assistant', content: responseText },
        { 
          role: 'user', 
          content: `Your previous response was not valid JSON. Error: ${parseErr.message}. Please output ONLY valid raw JSON matching the requested schema. Do not include markdown code fences or conversational text.` 
        }
      ];

      try {
        const retryRes = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model,
            messages: retryMessages,
            temperature: 0.0,
            response_format: { type: 'json_object' }
          })
        });

        if (!retryRes.ok) throw new Error(`Retry HTTP error! status: ${retryRes.status}`);

        const retryData = await retryRes.json();
        const retryText = retryData.choices?.[0]?.message?.content || '';
        const cleanedRetry = cleanJson(retryText);
        return JSON.parse(cleanedRetry);
      } catch (retryErr) {
        traceError(`Self-correction also failed: ${retryErr.message}`);
        throw new Error(`Inference returned malformed JSON on both original and retry: ${parseErr.message}`);
      }
    }
  }

  return responseText;
}
