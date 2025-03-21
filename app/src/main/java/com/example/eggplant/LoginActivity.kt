package com.example.eggplant

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_loading)
        val biometricManager = BiometricManager.from(this)
        when(biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)){
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt()
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                showToast("No biometrics hardware")
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                showToast("Biometrics hardware not available")
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                showToast("No registered biometrics")
            }
        }
    }

    private fun showBiometricPrompt(){
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showToast("Authentication Successful")
                    val progressBar: ProgressBar = findViewById(R.id.progressBar)
                    progressBar.max = 100

                    val currentProgress = 100
                    val animator = ObjectAnimator.ofInt(progressBar, "progress", currentProgress)
                        .setDuration(2000)

                    animator.addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                        }
                        override fun onAnimationEnd(animation: Animator) {
                            // Navigate to the next activity
                            val intent = Intent(this@LoginActivity, menu::class.java)
                            startActivity(intent)
                            finish()
                        }
                        override fun onAnimationCancel(animation: Animator) {

                        }
                        override fun onAnimationRepeat(animation: Animator) {
                        }
                    })
                    animator.start()

                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showToast("Error $errString")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showToast("Authentication Failed")
                }

            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometrics Authentication")
            .setSubtitle("Use your fingerprint to continue")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showToast(message: String){
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}