package com.example.trackerfinder.findhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.trackerfinder.ui.TrackerFinderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The two one-time Google steps, in an in-app browser (ported from Tag Tracker Open):
 *  - SIGN_IN: Google's sign-in page; we read the "oauth_token" cookie it sets and exchange it.
 *  - UNLOCK: Google's "unlock encrypted data" page where you enter your phone's screen lock;
 *    it hands the Find Hub decryption keys to window.mm.
 * You type your password and PIN into Google's own page; the app never sees them.
 */
class GoogleAuthActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var done = false
    private var title by mutableStateOf("")
    private var busy by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var unlockLoaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SIGN_IN
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)

        if (mode == MODE_SIGN_IN) {
            title = "Step 1: Sign in to Google"
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) { busy = false; checkCookie() }
            }
            // Start clean so a leftover token from a previous try isn't exchanged.
            CookieManager.getInstance().removeAllCookies {
                web.loadUrl("https://accounts.google.com/EmbeddedSetup")
                lifecycleScope.launch { while (!done) { delay(1000); checkCookie() } }
            }
        } else {
            title = "Step 2: Unlock encryption"
            Toast.makeText(this, "Sign in again, then enter your phone's screen lock", Toast.LENGTH_LONG).show()
            web.addJavascriptInterface(Bridge(), "mm")
            web.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    pageHost = runCatching { android.net.Uri.parse(url).host }.getOrNull()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    pageHost = runCatching { android.net.Uri.parse(url).host }.getOrNull()
                    busy = false
                    // Accept vaultKeys passed as a JS object too (Java params only take strings).
                    view.evaluateJavascript(
                        "(function(){var j=window.mm;if(!j||j.__w)return;" +
                            "window.mm={__w:1,setVaultSharedKeys:function(s,k){j.setVaultSharedKeys(String(s)," +
                            "typeof k==='string'?k:JSON.stringify(k));},closeView:function(){j.closeView();}};})()", null,
                    )
                    // The unlock page needs a normal accounts.google.com session; load it once signed in.
                    if (!unlockLoaded && url != null && url.startsWith("https://myaccount.google.com")) {
                        unlockLoaded = true
                        busy = true
                        lifecycleScope.launch {
                            try {
                                web.loadUrl(FindHub.sharedKeyUrl(this@GoogleAuthActivity))
                            } catch (e: Exception) {
                                error = FindHub.friendly(e)
                            }
                        }
                    }
                }
            }
            CookieManager.getInstance().removeAllCookies { web.loadUrl("https://accounts.google.com/") }
        }

        setContent {
            TrackerFinderTheme {
                BackHandler { if (web.canGoBack()) web.goBack() else finish() }
                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Filled.Close, "Close") } },
                    )
                }) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        AndroidView({ web }, Modifier.fillMaxSize())
                    }
                }
                error?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Something went wrong") },
                        text = { Text(msg) },
                        confirmButton = { TextButton(onClick = { finish() }) { Text("OK") } },
                    )
                }
            }
        }
    }

    private var lastTried = ""
    private var lastTryMs = 0L
    private var inFlight = false
    private var attempts = 0

    private fun checkCookie() {
        if (done || inFlight || attempts >= MAX_ATTEMPTS) return
        val cookies = CookieManager.getInstance().getCookie("https://accounts.google.com") ?: return
        val token = cookies.split(";").map { it.trim().split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "oauth_token" }?.get(1)
        if (token.isNullOrEmpty()) return
        // The page sets oauth_token early, before sign-in finishes; that one fails with
        // BadAuthentication. Retry the same value now and then, and any new value at once.
        val now = System.currentTimeMillis()
        if (token == lastTried && now - lastTryMs < 6000) return
        lastTried = token; lastTryMs = now; inFlight = true; attempts++
        lifecycleScope.launch {
            try {
                val email = FindHub.signIn(this@GoogleAuthActivity, token)
                done = true
                Toast.makeText(this@GoogleAuthActivity, "Connected $email", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK); finish()
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (!(msg.contains("BadAuthentication") || msg.contains("try again"))) {
                    done = true
                    error = FindHub.friendly(e)
                }
            } finally {
                inFlight = false
                if (!done && attempts >= MAX_ATTEMPTS) {
                    error = "Sign-in didn't complete, so the app stopped retrying to protect your account. Try again later."
                }
            }
        }
    }

    /** Host of the page now showing; the key bridge only listens to Google's own pages. */
    @Volatile private var pageHost: String? = null
    private fun trustedPage() = pageHost?.let { it == "google.com" || it.endsWith(".google.com") } == true

    inner class Bridge {
        @JavascriptInterface
        fun setVaultSharedKeys(@Suppress("UNUSED_PARAMETER") str: String?, vaultKeys: String?) {
            if (!trustedPage()) return
            if (done || vaultKeys == null || vaultKeys == "undefined") return
            done = true
            lifecycleScope.launch {
                try {
                    FindHub.saveVaultKeys(this@GoogleAuthActivity, vaultKeys)
                    Toast.makeText(this@GoogleAuthActivity, "Encryption unlocked", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK); finish()
                } catch (e: Exception) {
                    done = false
                    error = FindHub.friendly(e)
                }
            }
        }

        @JavascriptInterface
        fun closeView() { if (trustedPage()) runOnUiThread { finish() } }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SIGN_IN = "sign_in"
        const val MODE_UNLOCK = "unlock"
        private const val MAX_ATTEMPTS = 20 // about 2 minutes at 6 s spacing; avoids hammering Google
    }
}
