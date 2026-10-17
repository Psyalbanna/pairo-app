package ai.pairo.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import org.json.JSONObject

class MainActivity : Activity() {

    companion object {
        private const val HOST = "https://stb.taild257cc.ts.net/"
        private const val RC_MIC = 11
        private const val RC_NOTIF = 12
    }

    private lateinit var web: WebView
    private lateinit var speech: Speech
    private lateinit var downloadReceiver: android.content.BroadcastReceiver
    private var pendingDownload = -1L
    private val prefs by lazy { getSharedPreferences("pairo", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#08110f"))
        setContentView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " PairoApp/1.0"
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                    allowed(Manifest.permission.RECORD_AUDIO)
                ) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "PairoNative")

        speech = Speech(this)
        speech.sink = object : Speech.Sink {
            override fun onReady() {
                js("window.pairoOnState&&pairoOnState('mendengar')")
            }

            override fun onPartial(text: String) {
                js("window.pairoOnPartial&&pairoOnPartial(${JSONObject.quote(text)})")
            }

            override fun onFinal(text: String) {
                js("window.pairoOnText&&pairoOnText(${JSONObject.quote(text)})")
            }

            override fun onError(code: String) {
                js("window.pairoOnError&&pairoOnError(${JSONObject.quote(code)})")
            }

            override fun onStopped() {
                js("window.pairoOnState&&pairoOnState('berhenti')")
                MicService.stop(this@MainActivity)
            }
        }
        Bus.onStop = { runOnUiThread { speech.stop() } }
        Bus.onStart = { runOnUiThread { js("window.pairoExternalStart&&pairoExternalStart()") } }

        downloadReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (intent?.action != android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != pendingDownload) return
                try {
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    val uri = dm.getUriForDownloadedFile(id) ?: return
                    startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                    )
                } catch (_: Exception) {
                }
            }
        }
        val filter = android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }

        ask(Manifest.permission.RECORD_AUDIO, RC_MIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ask(Manifest.permission.POST_NOTIFICATIONS, RC_NOTIF)
        }

        showTokenGate()
    }

    private fun showTokenGate() {
        val saved = prefs.getString("token", "").orEmpty()
        if (saved.isNotEmpty()) {
            load(saved)
            return
        }

        val input = EditText(this).apply {
            hint = getString(R.string.token_hint)
            setSingleLine(true)
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.token_title)
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(R.string.token_ok) { _, _ ->
                val v = input.text.toString().trim()
                if (v.isEmpty()) showTokenGate() else {
                    prefs.edit().putString("token", v).apply()
                    load(v)
                }
            }
            .show()
    }

    private fun load(token: String) {
        web.loadUrl(HOST + "?k=" + Uri.encode(token))
    }

    private fun js(script: String) {
        runOnUiThread { web.evaluateJavascript(script, null) }
    }

    private fun allowed(perm: String) =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun ask(perm: String, code: Int) {
        if (!allowed(perm)) requestPermissions(arrayOf(perm), code)
    }

    inner class Bridge {
        @JavascriptInterface
        fun version(): String = "1"

        @JavascriptInterface
        fun startRec(lang: String) {
            runOnUiThread {
                speech.lang = if (lang.isBlank()) "id-ID" else lang
                speech.start()
                // layanan latar depan bertipe mikrofon menolak start tanpa izin rekam
                try {
                    if (allowed(Manifest.permission.RECORD_AUDIO)) MicService.start(this@MainActivity)
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun stopRec() {
            runOnUiThread { speech.stop() }
        }

        /** Meredam mikrofon selama suara saya keluar, supaya tidak merekam diri sendiri. */
        @JavascriptInterface
        fun muteRec() {
            runOnUiThread { speech.pause() }
        }

        @JavascriptInterface
        fun unmuteRec() {
            runOnUiThread { speech.resume() }
        }

        @JavascriptInterface
        fun versionCode(): Int = try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt()
            else @Suppress("DEPRECATION") pi.versionCode
        } catch (_: Exception) {
            0
        }

        @JavascriptInterface
        fun openSettings() {
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)
                    )
                )
            } catch (_: Exception) {
            }
        }

        /** Mengunduh APK pembaruan lalu membuka pemasangnya. */
        @JavascriptInterface
        fun installApk(url: String) {
            runOnUiThread {
                try {
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    val req = android.app.DownloadManager.Request(Uri.parse(url))
                        .setTitle("P.A.I.R.O")
                        .setDescription(getString(R.string.update_downloading))
                        .setNotificationVisibility(
                            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationInExternalFilesDir(this@MainActivity, null, "pairo-update.apk")
                        .setMimeType("application/vnd.android.package-archive")
                    pendingDownload = dm.enqueue(req)
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun buzz(ms: Int) {
            try {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(
                        android.os.VibrationEffect.createOneShot(
                            ms.toLong().coerceIn(5L, 80L),
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    v.vibrate(ms.toLong().coerceIn(5L, 80L))
                }
            } catch (_: Exception) {
            }
        }

        @JavascriptInterface
        fun setToken(t: String) {
            prefs.edit().putString("token", t.trim()).apply()
        }

        @JavascriptInterface
        fun hasToken(): Boolean = prefs.getString("token", "").orEmpty().isNotEmpty()
    }

    override fun onDestroy() {
        Bus.onStop = null
        Bus.onStart = null
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
        MicService.stop(this)
        speech.destroy()
        web.destroy()
        super.onDestroy()
    }
}
