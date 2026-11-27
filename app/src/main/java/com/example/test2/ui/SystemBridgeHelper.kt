package com.example.test2.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.example.test2.security.DeviceAuthManager
import com.example.test2.util.AppLogger

/**
 * UI-layer bridge for Android system Activities, biometric prompts, and Settings intents
 * so Activities stay free of direct security/manager plumbing.
 */
object SystemBridgeHelper {

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        noCredentialMessage: String = "Thiết bị chưa cài đặt mã khóa màn hình (PIN/Vân tay). Vui lòng thiết lập trong Cài đặt của máy."
    ) {
        DeviceAuthManager.authenticate(
            activity = activity,
            onSuccess = onSuccess,
            onError = { errorCode, _ ->
                if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                    Toast.makeText(activity, noCredentialMessage, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    fun openManageAllFilesSettings(activity: FragmentActivity) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
            } catch (e: Exception) {
                AppLogger.w("SystemBridgeHelper", "Falling back to generic all-files settings", e)
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.w("SystemBridgeHelper", "Failed to open all-files settings", e)
        }
    }

    fun openDeviceLockSettings(activity: FragmentActivity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (e: Exception) {
            AppLogger.w("SystemBridgeHelper", "Falling back to general settings", e)
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
