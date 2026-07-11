package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the generated single-file web app live in a [WebView] — the actual,
 * interactive result of a Forge run (not a mock or a code listing).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveAppPreview(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
        }
    )
}
