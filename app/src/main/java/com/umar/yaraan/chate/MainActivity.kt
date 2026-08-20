package com.umar.yaraan.chate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.umar.yaraan.chate.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : BaseImmersiveActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private var pendingPermissionRequest: PermissionRequest? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val WEB_APP_URL = "https://yaraan-voice-chat.netlify.app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupWebView()
        loadWebAppWithAuthSession()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = false
        webSettings.loadWithOverviewMode = false
        webSettings.textZoom = 100
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        binding.webView.addJavascriptInterface(object : Any() {
            @android.webkit.JavascriptInterface
            fun onUserLoggedOut() {
                runOnUiThread {
                    auth.signOut()
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }, "AndroidBridge")

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.webViewProgressBar.visibility = View.GONE
                injectAuthSessionToWebView()
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    val requestedResources = it.resources
                    val permissionsNeeded = mutableListOf<String>()

                    for (resource in requestedResources) {
                        if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            permissionsNeeded.add(Manifest.permission.CAMERA)
                        }
                    }

                    val notGranted = permissionsNeeded.filter { perm ->
                        ContextCompat.checkSelfPermission(this@MainActivity, perm) != PackageManager.PERMISSION_GRANTED
                    }

                    if (notGranted.isEmpty()) {
                        it.grant(requestedResources)
                    } else {
                        pendingPermissionRequest = it
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            notGranted.toTypedArray(),
                            PERMISSION_REQUEST_CODE
                        )
                    }
                }
            }
        }
    }

    private fun loadWebAppWithAuthSession() {
        binding.webViewProgressBar.visibility = View.VISIBLE
        binding.webView.loadUrl(WEB_APP_URL)
    }

    private fun injectAuthSessionToWebView() {
        val user = auth.currentUser ?: return
        user.getIdToken(true).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token ?: ""
                val uid = user.uid
                val email = user.email ?: ""
                val displayName = user.displayName ?: if (email.isNotEmpty()) email.substringBefore("@") else "User_$uid"
                val photoUrl = user.photoUrl?.toString() ?: ""

                val userDataMap = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "photoURL" to photoUrl,
                    "idToken" to token,
                    "isLoggedIn" to true
                )
                val userDataJson = JSONObject(userDataMap).toString()
                val quotedUserData = JSONObject.quote(userDataJson)
                val quotedToken = JSONObject.quote(token)
                val quotedUid = JSONObject.quote(uid)

                val jsInject = """
                    (function() {
                        try {
                            var uData = $quotedUserData;
                            var tok = $quotedToken;
                            var uId = $quotedUid;

                            localStorage.setItem('user_logged_in', 'true');
                            localStorage.setItem('privacy_accepted', 'true');
                            localStorage.setItem('user_id', uId);
                            localStorage.setItem('auth_token', tok);
                            localStorage.setItem('yaraan_user', uData);
                            localStorage.setItem('firebase_user', uData);
                            localStorage.setItem('is_logged_in', 'true');

                            var meta = document.querySelector('meta[name="viewport"]');
                            if (!meta) {
                                meta = document.createElement('meta');
                                meta.name = 'viewport';
                                document.getElementsByTagName('head')[0].appendChild(meta);
                            }
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                            var style = document.getElementById('native-view-fix-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'native-view-fix-style';
                                style.innerHTML = '#view-auth, #email-auth-full-page, .auth-container, .login-modal { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; } html, body { width: 100% !important; max-width: 100% !important; overflow-x: hidden !important; -webkit-text-size-adjust: 100% !important; } .bottom-nav, .navigation, footer, nav { display: flex !important; visibility: visible !important; opacity: 1 !important; z-index: 9999 !important; }';
                                document.head.appendChild(style);
                            }

                            var authView = document.getElementById('view-auth');
                            if (authView) {
                                authView.style.display = 'none';
                                authView.classList.remove('active');
                            }

                            var fullAuthPage = document.getElementById('email-auth-full-page');
                            if (fullAuthPage) {
                                fullAuthPage.style.display = 'none';
                            }

                            var loginForm = document.querySelector('.login-screen, .auth-container, #loginForm, [class*="login"]');
                            if (loginForm) {
                                loginForm.style.display = 'none';
                            }

                            var mainView = document.getElementById('view-home') || document.querySelector('.main-app, .dashboard, #app');
                            if (mainView) {
                                mainView.style.display = 'block';
                                mainView.classList.add('active');
                            }

                            window.dispatchEvent(new Event('storage'));
                            if (typeof window.onNativeAuthSuccess === 'function') {
                                window.onNativeAuthSuccess(JSON.parse(uData));
                            }
                        } catch(e) {
                            console.error('Failed to inject auth state', e);
                        }
                    })();
                """.trimIndent()

                binding.webView.evaluateJavascript(jsInject, null)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val pending = pendingPermissionRequest
            if (pending != null) {
                val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    pending.grant(pending.resources)
                } else {
                    pending.deny()
                    Toast.makeText(this, "Microphone/Camera permission required for voice chat features", Toast.LENGTH_SHORT).show()
                }
                pendingPermissionRequest = null
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
