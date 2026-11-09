package com.example.lifetrack.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 通过 FileProvider 暴露本地 APK 并调起系统安装器。
 * Android 8.0+ 需要 `REQUEST_INSTALL_PACKAGES` 权限，首次安装未知来源应用会由系统弹窗要求用户授权。
 */
object ApkInstaller {
    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
