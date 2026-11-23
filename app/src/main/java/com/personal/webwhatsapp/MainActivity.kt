package com.personal.webwhatsapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    private var desktopMode = false
    private lateinit var mobileUserAgent: String

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var desktopScript: ScriptHandler? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = android.webkit.WebChromeClient.FileChooserParams
                .parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                val granted = request.resources.filter { res ->
                    when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> hasPermission(Manifest.permission.CAMERA)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> hasPermission(Manifest.permission.RECORD_AUDIO)
                        else -> false
                    }
                }.toTypedArray()
                if (granted.isNotEmpty()) request.grant(granted) else request.deny()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        mobileUserAgent = webView.settings.userAgentString

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        applyDesktopMode()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return false
                // WhatsApp's own pages stay inside the app, everything else opens externally.
                if (host == "whatsapp.com" || host.endsWith(".whatsapp.com") ||
                    host.endsWith(".whatsapp.net")
                ) {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = mutableListOf<String>()
                    request.resources.forEach { res ->
                        if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                            !hasPermission(Manifest.permission.CAMERA)
                        ) needed.add(Manifest.permission.CAMERA)
                        if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                            !hasPermission(Manifest.permission.RECORD_AUDIO)
                        ) needed.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (needed.isEmpty()) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest?.deny()
                        pendingPermissionRequest = request
                        mediaPermissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("http")) {
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("User-Agent", userAgent)
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType)
                        )
                    }
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "Download shuru hua", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Download nahi ho paya", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Is file ka download support nahi hai", Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(WHATSAPP_URL)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        updateToggleIcon(menu.findItem(R.id.action_desktop_mode))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_desktop_mode) {
            desktopMode = !desktopMode
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DESKTOP_MODE, desktopMode).apply()
            applyDesktopMode()
            updateToggleIcon(item)
            Toast.makeText(
                this,
                if (desktopMode) R.string.desktop_mode_on else R.string.desktop_mode_off,
                Toast.LENGTH_SHORT
            ).show()
            webView.reload()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateToggleIcon(item: MenuItem?) {
        item ?: return
        item.setIcon(if (desktopMode) R.drawable.ic_desktop_on else R.drawable.ic_desktop_off)
        item.title = getString(if (desktopMode) R.string.desktop_mode_on else R.string.desktop_mode_off)
    }

    private fun applyDesktopMode() {
        webView.settings.apply {
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else mobileUserAgent
            useWideViewPort = desktopMode
            loadWithOverviewMode = desktopMode
            setSupportZoom(desktopMode)
            builtInZoomControls = desktopMode
            displayZoomControls = false
        }

        // Make the page believe this is a real desktop Chrome (client hints, platform, touch).
        desktopScript?.remove()
        desktopScript = null
        if (desktopMode && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            desktopScript = WebViewCompat.addDocumentStartJavaScript(
                webView,
                DESKTOP_SPOOF_JS,
                setOf("https://web.whatsapp.com")
            )
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val WHATSAPP_URL = "https://web.whatsapp.com"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

        private const val DESKTOP_SPOOF_JS = """
(function () {
  try {
    var brands = [
      {brand: 'Chromium', version: '142'},
      {brand: 'Google Chrome', version: '142'},
      {brand: 'Not_A Brand', version: '99'}
    ];
    var uad = {
      brands: brands,
      mobile: false,
      platform: 'Windows',
      getHighEntropyValues: function () {
        return Promise.resolve({
          brands: brands, mobile: false, platform: 'Windows',
          platformVersion: '10.0.0', architecture: 'x86', bitness: '64',
          model: '', uaFullVersion: '142.0.0.0'
        });
      },
      toJSON: function () { return {brands: brands, mobile: false, platform: 'Windows'}; }
    };
    Object.defineProperty(Navigator.prototype, 'userAgentData', {get: function () { return uad; }, configurable: true});
    Object.defineProperty(Navigator.prototype, 'platform', {get: function () { return 'Win32'; }, configurable: true});
    Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {get: function () { return 0; }, configurable: true});
  } catch (e) {}
})();
"""
    }
}
