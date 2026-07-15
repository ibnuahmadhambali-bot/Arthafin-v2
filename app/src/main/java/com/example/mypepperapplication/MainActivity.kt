package com.example.mypepperapplication

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity - Dibuat oleh Senior Android Developer.
 * Implementasi WebView Android yang sangat kokoh (robust) dan terasa 100% native.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. TAMPILAN FULL NATIVE: Sembunyikan Action Bar / Title Bar agar full screen
        supportActionBar?.hide()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_main)

        // Temukan view WebView dari layout xml
        webView = findViewById(R.id.webView)

        // Konfigurasi performa & fungsionalitas WebView
        setupWebView()

        // Muat URL utama
        webView.loadUrl("file:///android_asset/index.html")

        // 3. PENANGANAN BACK BUTTON (Menggunakan API OnBackPressed terbaru yang kokoh)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack() // Mundur di riwayat web
                } else {
                    showExitDialog() // Dialog konfirmasi keluar
                }
            }
        })
    }

    private fun setupWebView() {
        val settings = webView.settings

        // JavaScript & DOM Storage (Wajib aktif)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        // Izinkan WebView membaca file lokal
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        // Membaca file lokal dari URL file lain
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // Matikan fungsi zoom agar tidak merusak layout native
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Dukungan responsif viewport layout
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Cache agresif untuk performa maksimal (Prioritas cache offline jika ada)
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        // Aktifkan Mixed Content agar file lokal bisa request AJAX/API ke HTTPS app.arthafin.web.id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Matikan efek over-scroll (bounce) agar terasa seperti aplikasi native
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        // Sembunyikan scrollbar vertikal dan horizontal
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Matikan seleksi teks / copy-paste panjang
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false
        
        // Interseptor ActionMode untuk mencegah menu seleksi teks muncul di beberapa device
        webView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }

        // WebClient untuk menangani routing link
        webView.webViewClient = MyCustomWebViewClient()
    }

    /**
     * Custom WebViewClient untuk merouting link eksternal dan meloloskan URL aplikasi utama.
     */
    private inner class MyCustomWebViewClient : WebViewClient() {

        @Deprecated("Deprecated in Java", ReplaceWith("shouldOverrideUrlLoading"))
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleUrlRouting(url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: android.webkit.WebResourceRequest?
        ): Boolean {
            val url = request?.url?.toString()
            return handleUrlRouting(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Matikan seleksi teks menggunakan CSS tambahan secara dinamis
            webView.evaluateJavascript(
                "var style = document.createElement('style');" +
                "style.innerHTML = '*, *:before, *:after { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }';" +
                "document.head.appendChild(style);",
                null
            )
        }
    }

    /**
     * Memproses link: Link HTTP/S internal dibuka di WebView,
     * sedangkan protocol luar (tel, mailto, whatsapp, dll) dilempar ke Intent HP.
     */
    private fun handleUrlRouting(url: String?): Boolean {
        if (url == null) return false

        // Deteksi skema deep-link eksternal
        if (url.startsWith("tel:") || 
            url.startsWith("mailto:") || 
            url.startsWith("whatsapp:") || 
            url.startsWith("intent:") || 
            url.startsWith("market:") || 
            url.startsWith("play.google.com")
        ) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                
                if (url.startsWith("intent:")) {
                    // Penanganan link format intent:// Android
                    try {
                        val parsedIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (parsedIntent != null) {
                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                            if (fallbackUrl != null) {
                                webView.loadUrl(fallbackUrl)
                                return true
                            }
                            // Jalankan aplikasi native tujuan
                            startActivity(parsedIntent)
                            return true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Aplikasi tidak ditemukan untuk link ini", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Protocol biasa (whatsapp://, tel:, mailto:)
                    intent.data = Uri.parse(url)
                    startActivity(intent)
                }
                return true
            } catch (e: ActivityNotFoundException) {
                // Tangani error dengan aman agar tidak force close
                Toast.makeText(this, "Aplikasi pendukung untuk link tersebut tidak terinstall", Toast.LENGTH_LONG).show()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return true
            }
        }

        // Untuk link HTTPS / HTTP normal, pastikan tetap dibuka DI DALAM aplikasi
        // Terutama yang menuju ke host utama (misal: app.arthafin.web.id)
        return false // Mengembalikan false agar WebView memuat URL di dalamnya
    }

    /**
     * Dialog konfirmasi native sebelum keluar aplikasi jika riwayat web habis.
     */
    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setPositiveButton("Ya") { _: DialogInterface, _: Int ->
                finish() // Keluar dari activity / tutup app
            }
            .setNegativeButton("Tidak", null)
            .show()
    }
}
