package com.example.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic offline fallback used only when no on-device model is present
 * (useMock = true). Mirrors the shape the real Gemma path returns so the agent
 * loop is coherent without a model: PLANNER/JUDGE return JSON objects, while
 * CODER/FIXER return { "text": "<html>..." } (the loop reads these as raw HTML).
 */
object MockModels {

    fun respond(role: InferenceRouter.Role, userPrompt: String): JSONObject = when (role) {
        InferenceRouter.Role.PLANNER -> planFor(userPrompt)
        InferenceRouter.Role.CODER -> JSONObject().put("text", webApp(extractObjective(userPrompt)))
        InferenceRouter.Role.FIXER -> JSONObject().put("text", webApp(extractObjective(userPrompt)))
        InferenceRouter.Role.JUDGE -> JSONObject().put("verdict", "pass").put("reason", "No runtime errors detected in sandbox.")
    }

    private fun planFor(userPrompt: String): JSONObject {
        val tasks = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1"); put("description", "Build the HTML structure and inline CSS layout"); put("depends_on", JSONArray())
            })
            put(JSONObject().apply {
                put("id", "2"); put("description", "Implement app logic and localStorage in vanilla JS"); put("depends_on", JSONArray().put("1"))
            })
        }
        return JSONObject().put("tasks", tasks)
    }

    private fun extractObjective(prompt: String): String {
        val m = Regex("""Objective:\s*"([^"]+)"""").find(prompt)
        return m?.groupValues?.getOrNull(1) ?: prompt.lineSequence().firstOrNull().orEmpty()
    }

    /** A small but genuinely working, self-contained offline note/list app. */
    private fun webApp(objective: String): String {
        val title = objective.ifBlank { "Forge App" }.replace("\"", "").take(48)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
  :root{color-scheme:light dark}
  *{box-sizing:border-box}
  body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:20px;background:#0f172a;color:#e2e8f0}
  h1{font-size:20px;margin:0 0 4px}
  p.sub{margin:0 0 16px;color:#94a3b8;font-size:13px}
  .row{display:flex;gap:8px;margin-bottom:12px}
  input{flex:1;padding:12px;border-radius:10px;border:1px solid #334155;background:#1e293b;color:#e2e8f0;font-size:15px}
  button{padding:12px 16px;border:0;border-radius:10px;background:#14b8a6;color:#062;font-weight:700;font-size:15px}
  ul{list-style:none;padding:0;margin:0}
  li{display:flex;justify-content:space-between;align-items:center;padding:12px;border-radius:10px;background:#1e293b;margin-bottom:8px}
  li button{background:#334155;color:#e2e8f0;padding:6px 10px;font-size:13px}
  .empty{color:#64748b;text-align:center;padding:24px 0}
</style>
</head>
<body>
  <h1>$title</h1>
  <p class="sub">Offline app — saved on this device.</p>
  <div class="row">
    <input id="entry" placeholder="Add an item..." autocomplete="off">
    <button id="add">Add</button>
  </div>
  <ul id="list"></ul>
  <script>
    var KEY = 'forge_items';
    var listEl = document.getElementById('list');
    var input = document.getElementById('entry');
    function load(){ try{ return JSON.parse(localStorage.getItem(KEY) || '[]'); }catch(e){ return []; } }
    function save(items){ localStorage.setItem(KEY, JSON.stringify(items)); }
    function render(){
      var items = load();
      listEl.innerHTML = '';
      if(!items.length){ var e=document.createElement('div'); e.className='empty'; e.textContent='Nothing yet — add your first item.'; listEl.appendChild(e); return; }
      items.forEach(function(text, i){
        var li=document.createElement('li');
        var span=document.createElement('span'); span.textContent=text; li.appendChild(span);
        var del=document.createElement('button'); del.textContent='Delete';
        del.addEventListener('click', function(){ var a=load(); a.splice(i,1); save(a); render(); });
        li.appendChild(del); listEl.appendChild(li);
      });
    }
    function add(){ var v=(input.value||'').trim(); if(!v) return; var a=load(); a.push(v); save(a); input.value=''; render(); }
    document.getElementById('add').addEventListener('click', add);
    input.addEventListener('keydown', function(ev){ if(ev.key==='Enter') add(); });
    render();
  </script>
</body>
</html>
""".trimIndent()
    }
}
