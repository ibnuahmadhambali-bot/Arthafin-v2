package com.example.mypepperapplication

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // <-- IMPORT MESIN SPLASH SCREEN
import java.io.IOException
import java.io.InputStream

/**
 * Senior Android WebView Controller
 * - Intercepts static asset calls and serves them locally from the APK assets.
 * - Handles Runtime Camera Permissions for Android OS.
 * - Automatically grants camera hardware access requests from internal web views.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val targetUrl = "https://app.arthafin.web.id"
    private val assetInterceptKeyword = "/assets/"
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // NYALAKAN MESIN SPLASH SCREEN DI SINI (WAJIB SEBELUM SUPER.ONCREATE)
        installSplashScreen() 
        
        super.onCreate(savedInstanceState)
        
        // 1. Hide Action Bar / Title Bar for full native UI
        supportActionBar?.hide()
        
        webView = WebView(this)
        setContentView(webView)

        // 2. WebView Settings Configuration
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false // Allow smooth inline camera feeds & video
        
        // Enable Hardware Acceleration for native fluid performance
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Zoom settings
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        
        // Scrollbar configuration
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        
        // Disable Over-scroll Bounce for clean webapp integration
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        // 3. WebChromeClient: Grant camera access request to Webapp
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request != null) {
                    val resources = request.resources
                    for (resource in resources) {
                        if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            // Automatically grant camera permission to HTML5 camera request
                            runOnUiThread {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                            }
                            return
                        }
                    }
                }
                super.onPermissionRequest(request)
            }
        }

        // 4. WebViewClient: Local assets interception & external redirects
        webView.webViewClient = object : WebViewClient() {
            
            // For Android 5.0+ (API 21+)
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                return interceptAssetRequest(url)
            }

            // Fallback for older Android versions (API < 21)
            @Deprecated("Deprecated in Java")
            override fun shouldInterceptRequest(
                view: WebView?,
                url: String?
            ): WebResourceResponse? {
                if (url == null) return null
                return interceptAssetRequest(url)
            }

            // Route external schemas (tel:, mailto:, whatsapp://, sms:) to System Apps
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleExternalIntent(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleExternalIntent(url)
            }
        }

        // 5. Request Android Camera Permission on Start
        checkAndRequestCameraPermission()

        // Load primary live PHP application URL
        webView.loadUrl(targetUrl)
    }

    /**
     * Checks if camera permission is granted. Requests if missing.
     */
    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Handle result of Android runtime permission dialog
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission granted! Reload to trigger any pending web integrations.
                webView.reload()
            }
        }
    }

    /**
     * Intercepts incoming requests and pulls matching resources from local APK assets.
     */
    private fun interceptAssetRequest(url: String): WebResourceResponse? {
        if (url.contains(assetInterceptKeyword)) {
            try {
                val uri = Uri.parse(url)
                val path = uri.path ?: return null
                
                // Strip leading slash for Android AssetManager mapping compatibility.
                val assetPath = if (path.startsWith("/")) path.substring(1) else path
                
                // Identify correct MIME type based on file extension
                val mimeType = getMimeType(assetPath) ?: "application/octet-stream"
                val encoding = "UTF-8"
                
                // Read from local APK 'src/main/assets/' directory
                val inputStream: InputStream = assets.open(assetPath)
                
                // Stream contents immediately with 0ms internet delay
                return WebResourceResponse(mimeType, encoding, inputStream)
            } catch (e: IOException) {
                // File missing in APK assets, fall back to default online loading
                e.printStackTrace()
            }
        }
        return null
    }

    /**
     * Resolve correct mime type to prevent WebView rendering blocks.
     */
    private fun getMimeType(filePath: String): String? {
        return when {
            filePath.endsWith(".css", true) -> "text/css"
            filePath.endsWith(".js", true) -> "application/javascript"
            filePath.endsWith(".png", true) -> "image/png"
            filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
            filePath.endsWith(".gif", true) -> "image/gif"
            filePath.endsWith(".svg", true) -> "image/svg+xml"
            filePath.endsWith(".ico", true) -> "image/x-icon"
            filePath.endsWith(".woff2", true) -> "font/woff2"
            filePath.endsWith(".woff", true) -> "font/woff"
            filePath.endsWith(".ttf", true) -> "font/ttf"
            filePath.endsWith(".json", true) -> "application/json"
            else -> null
        }
    }

    /**
     * Handle non-http schemas via standard system activities
     */
    private fun handleExternalIntent(url: String): Boolean {
        if (url.startsWith("tel:") || 
            url.startsWith("mailto:") || 
            url.startsWith("whatsapp://") || 
            url.startsWith("sms:") || 
            url.startsWith("intent:")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true // Handled by System
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false // Let WebView load normally
    }

    /**
     * UX: Implement back history or finish activity on back press
     */
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
