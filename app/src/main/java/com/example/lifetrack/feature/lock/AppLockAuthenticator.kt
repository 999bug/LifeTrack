package com.example.lifetrack.feature.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * 应用锁的系统认证封装。
 *
 * - Android 11（API 30）及以上：平台 `BiometricPrompt` 同时允许「生物识别 + 设备凭据」，
 *   一个系统弹窗就能覆盖「有指纹」和「只有 PIN / 图案 / 密码」两种情况。
 * - Android 10 及以下：平台 BiometricPrompt 不支持把设备凭据并进同一个弹窗，
 *   直接用 `KeyguardManager` 的系统凭据页兜底，避免自己拼一套密码输入界面。
 *
 * 不做任何口令校验，也不接触数据库密钥；认证结果只决定是否展示界面。
 */
class AppLockAuthenticator(private val activity: Activity) {

    /** 设备是否具备可用的锁屏保护；没有锁屏密码的设备不允许开启应用锁。 */
    fun isAvailable(): Boolean {
        val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return false
        if (!keyguard.isDeviceSecure) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = activity.getSystemService(BiometricManager::class.java) ?: return true
            val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
        }
        return true
    }

    /** API 30 以下使用：系统设备凭据页。返回 null 表示设备没有可用的锁屏保护。 */
    @Suppress("DEPRECATION") // 仅在 API 26–29 调用；新系统走 SystemBiometricPrompt。
    fun credentialIntent(): Intent? {
        val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return null
        return keyguard.createConfirmDeviceCredentialIntent("解锁生活记", "验证设备密码")
    }
}

/** 单独收口 API 30 的类引用，避免 API 26–29 的设备在加载认证类时碰到不存在的类型。 */
object SystemBiometricPrompt {

    @RequiresApi(Build.VERSION_CODES.R)
    fun authenticate(
        activity: Activity,
        signal: CancellationSignal,
        onSuccess: () -> Unit,
        onError: (message: String?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("解锁生活记")
            .setSubtitle("使用指纹或设备密码")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(
            signal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorMessage(errorCode))
                }
            }
        )
    }

    private fun errorMessage(errorCode: Int): String? = when (errorCode) {
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> null

        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
            "尝试次数过多，请稍后再试，或使用设备密码。"

        else -> "验证失败，请重试。"
    }
}

/** 设置页只需要判断能力，不需要构造 Activity 绑定对象。 */
fun canUseAppLock(context: Context): Boolean {
    val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
    if (!keyguard.isDeviceSecure) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return true
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
    }
    return true
}
