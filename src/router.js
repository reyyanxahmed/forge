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
        { id: "1", description: "Design Jetpack Compose layouts and Material You theme colors inside MainScreen.kt and Color.kt", depends_on: [] },
        { id: "2", description: "Implement interactive calculation logic and state handling in MainScreenViewModel.kt", depends_on: ["1"] }
      ]
    };
  }

  if (roleLower === 'coder') {
    // Determine which task we are coder for
    if (lastMsg.includes('Design') || lastMsg.includes('task 1') || lastMsg.includes('"id": "1"')) {
      const file = path.join(fixturesDir, 'coder_task1.json');
      if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
      return {
        operations: [
          { op: "write", path: "app/src/main/java/com/example/testapp/theme/Color.kt", content: 'package com.example.testapp.theme\n\nimport androidx.compose.ui.graphics.Color\n\nval Purple80 = Color(0xFFD0BCFF)\nval PurpleGrey80 = Color(0xFFCCC2DC)\n' },
          { op: "write", path: "app/src/main/java/com/example/testapp/ui/main/MainScreen.kt", content: 'package com.example.testapp.ui.main\n\nimport androidx.compose.runtime.Composable\n\n@Composable\nfun MainScreen() {\n    // UI Layout\n}\n' }
        ]
      };
    }
    
    // For other tasks
    const file = path.join(fixturesDir, 'coder_task2.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    return {
      operations: [
        { op: "write", path: "app/src/main/java/com/example/testapp/ui/main/MainScreenViewModel.kt", content: 'package com.example.testapp.ui.main\n\nimport androidx.lifecycle.ViewModel\n\nclass MainScreenViewModel : ViewModel() {\n    // calculation logic\n}\n' }
      ]
    };
  }

  if (roleLower === 'fixer') {
    const file = path.join(fixturesDir, 'fixer.json');
    if (fs.existsSync(file)) return JSON.parse(fs.readFileSync(file, 'utf8'));
    return {
      explanation: "Fixed missing closing bracket",
      operations: [
        { op: "write", path: "app/src/main/java/com/example/testapp/ui/main/MainScreen.kt", content: 'package com.example.testapp.ui.main\n\nimport androidx.compose.runtime.Composable\n\n@Composable\nfun MainScreen() {\n    // UI Layout (Fixed)\n}\n' }
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

export async function infer({ role, messages, expect, mock = false, sketchPath = null }) {
  const model = ROLE_TO_MODEL[role] || 'gemma-4-e2b';
  const start = Date.now();

  let formattedMessages = [...messages];
  if (sketchPath && fs.existsSync(sketchPath)) {
    try {
      const mimeTypes = {
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.webp': 'image/webp'
      };
      const ext = path.extname(sketchPath).toLowerCase();
      const mime = mimeTypes[ext] || 'image/png';
      const fileData = fs.readFileSync(sketchPath);
      const base64Img = fileData.toString('base64');
      const dataUrl = `data:${mime};base64,${base64Img}`;
      
      const lastUserMsgIndex = formattedMessages.map(m => m.role).lastIndexOf('user');
      if (lastUserMsgIndex !== -1) {
        const textContent = formattedMessages[lastUserMsgIndex].content;
        formattedMessages[lastUserMsgIndex] = {
          role: 'user',
          content: [
            { type: 'text', text: textContent },
            { type: 'image_url', image_url: { url: dataUrl } }
          ]
        };
      }
    } catch (err) {
      traceError(`Failed to process sketch image: ${err.message}`);
    }
  }

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
    messages: formattedMessages,
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
