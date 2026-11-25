package com.example.test2.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object DeviceAuthManager {

    private fun getAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // Required for API < 30: BIOMETRIC_WEAK is needed for device credentials on Android 10 and lower
            @Suppress("DEPRECATION")
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    /**
     * Checks if the device has biometric hardware, enrolled credentials, or screen lock (PIN/pattern/password).
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val status = biometricManager.canAuthenticate(getAuthenticators())
        return status == BiometricManager.BIOMETRIC_SUCCESS ||
                status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    /**
     * Checks specifically if the user has configured at least one secure lock screen (PIN, Pattern, Password, or Biometric).
     * Prevents lockouts where BiometricPrompt fails with ERROR_NO_DEVICE_CREDENTIAL.
     */
    fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    /**
     * Prompts the user to authenticate using device screen lock (fingerprint, face, or device PIN/pattern/password).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Mở khóa Két bảo mật",
        subtitle: String = "Dùng vân tay, khuôn mặt hoặc mã khóa màn hình của máy",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(getAuthenticators())
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
