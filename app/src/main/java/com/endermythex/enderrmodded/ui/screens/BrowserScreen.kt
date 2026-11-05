package com.endermythex.enderrmodded.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.core.Http
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Page APKMirror intégrée : l'utilisateur valide la vérification anti-robot du site (captcha),
 * le téléchargement est intercepté puis [onDownloaded] reprend le patch automatique.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ApkMirrorWebView(page: ApkMirrorSource.DownloadPage, modifier: Modifier = Modifier, onDownloaded: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember(page) { mutableStateOf<Float?>(null) }
    var status by remember(page) { mutableStateOf("Valide la vérification ci-dessous si elle s'affiche : le téléchargement démarre ensuite tout seul.") }

    Column(modifier) {
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        progress?.let { p -> WavyLinearProgress(p) }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        // On reste sur APKMirror (et le domaine de vérification Cloudflare).
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            return !(host.endsWith("apkmirror.com") || host.endsWith("cloudflare.com"))
                        }
                    }
                    setDownloadListener { url, userAgent, _, _, _ ->
                        if (progress != null) return@setDownloadListener
                        progress = 0f
                        status = "Téléchargement…"
                        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                        val dest = File(context.cacheDir, "browser/${page.packageName}.${if (page.isBundle) "apkm" else "apk"}")
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    dest.parentFile?.mkdirs()
                                    Http.download(
                                        url, dest,
                                        mapOf("Cookie" to cookies, "User-Agent" to userAgent, "Referer" to page.url),
                                    ) { done, total -> if (total > 0) progress = done.toFloat() / total }
                                }
                                status = "Téléchargé. Reprise du patch…"
                                onDownloaded(dest)
                            } catch (t: Throwable) {
                                progress = null
                                status = "Échec du téléchargement : ${t.message}. Réessaie."
                            }
                        }
                    }
                    loadUrl(page.url)
                }
            },
        )
    }
}

/** Écran plein (ouvert depuis une notification ou un lien). */
@Composable
fun BrowserScreen(vm: MainViewModel, page: ApkMirrorSource.DownloadPage) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Télécharger ${AppCatalog.nameOf(page.packageName)}", subtitle = "APKMirror • v${page.version}", onBack = { vm.back() })
        ApkMirrorWebView(page, Modifier.weight(1f)) { file -> vm.autoPatchWithFile(page.packageName, file) }
    }
}
