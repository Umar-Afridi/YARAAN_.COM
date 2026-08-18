package com.umar.yaraan.chate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.umar.yaraan.chate.databinding.ActivityLoginBinding

class LoginActivity : BaseImmersiveActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val FALLBACK_WEB_CLIENT_ID = "740464208491-r63hohlm9o2lvc40f8gffitrbe6pceq8.apps.googleusercontent.com"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                firebaseAuthWithGoogle(idToken)
            } ?: run {
                hideLoading()
                Toast.makeText(this, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            hideLoading()
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupGoogleSignIn()
        setupBackgroundVideo()
        setupUIEvents()
        setupPrivacyPolicyFooter()
    }

    private fun setupGoogleSignIn() {
        try {
            val webClientId = try {
                getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                FALLBACK_WEB_CLIENT_ID
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GoogleSignInClient", e)
        }
    }

    private fun setupBackgroundVideo() {
        try {
            val videoUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.bg_login)
            binding.videoView.setVideoURI(videoUri)
            binding.videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
                mediaPlayer.setVolume(0f, 0f)
                binding.videoView.start()
            }
            binding.videoView.setOnErrorListener { _, _, _ ->
                true // Suppress errors so video failure won't crash UI
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up background video", e)
        }
    }

    private fun setupUIEvents() {
        binding.btnGoogleSignIn.setOnClickListener {
            val client = googleSignInClient
            if (client != null) {
                showLoading()
                val signInIntent = client.signInIntent
                googleSignInLauncher.launch(signInIntent)
            } else {
                Toast.makeText(this, "Google Sign-In not initialized", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEmailToggle.setOnClickListener {
            if (binding.cardEmailForm.visibility == View.VISIBLE) {
                binding.cardEmailForm.visibility = View.GONE
            } else {
                binding.cardEmailForm.visibility = View.VISIBLE
            }
        }

        binding.btnLoginSubmit.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty()) {
                binding.tilEmail.error = "Email is required"
                return@setOnClickListener
            }
            binding.tilEmail.error = null

            if (password.isEmpty()) {
                binding.tilPassword.error = "Password is required"
                return@setOnClickListener
            }
            binding.tilPassword.error = null

            showLoading()
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        onAuthSuccess()
                    } else {
                        hideLoading()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.tvSignUp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password to create account", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    } else {
                        hideLoading()
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmail.error = "Enter your email to reset password"
                return@setOnClickListener
            }
            binding.tilEmail.error = null

            showLoading()
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    hideLoading()
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    onAuthSuccess()
                } else {
                    hideLoading()
                    Toast.makeText(this, "Authentication Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupPrivacyPolicyFooter() {
        val prefix = getString(R.string.privacy_policy_prefix)
        val linkText = getString(R.string.privacy_policy_link)
        val fullText = "$prefix$linkText"

        val spannableString = SpannableString(fullText)
        val startIndex = prefix.length
        val endIndex = fullText.length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val url = "https://docs.google.com/document/d/1Mq4m80_848fEtMh4t3S1CZaj4yQEczCQCbWVfC_TDmM/edit?usp=drivesdk"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }

        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.yellow_accent)),
            startIndex,
            endIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvPrivacyPolicy.text = spannableString
        binding.tvPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showLoading() {
        binding.cardLoadingPopup.visibility = View.VISIBLE
        binding.btnGoogleSignIn.isEnabled = false
        binding.btnEmailToggle.isEnabled = false
        binding.btnLoginSubmit.isEnabled = false
    }

    private fun hideLoading() {
        binding.cardLoadingPopup.visibility = View.GONE
        binding.btnGoogleSignIn.isEnabled = true
        binding.btnEmailToggle.isEnabled = true
        binding.btnLoginSubmit.isEnabled = true
    }

    private fun onAuthSuccess() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!binding.videoView.isPlaying) {
                binding.videoView.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
