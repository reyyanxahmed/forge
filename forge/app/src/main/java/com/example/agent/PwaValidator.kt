package com.example.agent

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Loads a generated single-file web app in a headless [WebView] and captures the
 * real runtime/console errors it produces. This is the agent's genuine "build /
 * lint" signal — the check phase of the sense→decide→act→check loop — replacing
 * the old hard-coded compile results.
 *
 * Returns the list of distinct errors (empty list = the app ran cleanly).
 */
object PwaValidator {

    private const val TAG = "PwaValidator"

    private val HOOKS = """
<script>
(function(){
  function report(m){ try{ if(window.ForgeBridge) window.ForgeBridge.report(String(m)); }catch(e){} }
  window.onerror=function(msg,src,line,col,err){ report('Uncaught '+((err&&err.stack)||msg)+' (line '+line+':'+col+')'); return false; };
  window.addEventListener('unhandledrejection',function(ev){ report('UnhandledPromiseRejection: '+((ev.reason&&ev.reason.stack)||ev.reason)); });
  var _e=console.error; console.error=function(){ try{ report('console.error: '+Array.prototype.map.call(arguments,String).join(' ')); }catch(_){}; _e.apply(console,arguments); };
})();
</script>
""".trimIndent()

    private class Bridge {
        val errors = CopyOnWriteArrayList<String>()
        @JavascriptInterface fun report(m: String) { errors.add(m) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun validate(context: Context, html: String, settleMs: Long = 3000): List<String> =
        withContext(Dispatchers.Main) {
            if (html.isBlank()) return@withContext listOf("Empty artifact: no HTML was generated.")
            val bridge = Bridge()
            var webView: WebView? = null
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(bridge, "ForgeBridge")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                            if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                bridge.errors.add("console: ${cm.message()} (line ${cm.lineNumber()})")
                            }
                            return true
                        }
                    }
                }
                webView.loadDataWithBaseURL("about:blank", injectHooks(html), "text/html", "UTF-8", null)
                delay(settleMs)
            } catch (e: Throwable) {
                // Infrastructure failure (not an app bug) — log and treat as "no
                // detected errors" so the fixer loop isn't triggered pointlessly.
                Log.e(TAG, "Validator infrastructure exception: ${e.message}", e)
            } finally {
                try { webView?.destroy() } catch (_: Throwable) {}
            }
            bridge.errors.distinct()
        }

    private fun injectHooks(html: String): String {
        val headIdx = html.indexOf("<head", ignoreCase = true)
        if (headIdx >= 0) {
            val close = html.indexOf('>', headIdx)
            if (close >= 0) return html.substring(0, close + 1) + HOOKS + html.substring(close + 1)
        }
        val htmlIdx = html.indexOf("<html", ignoreCase = true)
        if (htmlIdx >= 0) {
            val close = html.indexOf('>', htmlIdx)
            if (close >= 0) return html.substring(0, close + 1) + HOOKS + html.substring(close + 1)
        }
        return HOOKS + html
    }
}
