package com.pulsepad.controller.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.pulsepad.controller.domain.engine.AvailableUpdate
import com.pulsepad.controller.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Downloads an APK with the system [DownloadManager] and hands it to the package
 * installer.
 *
 * Using DownloadManager rather than an in-process download means the transfer
 * survives PulsePad being backgrounded, and it is the same path the browser uses,
 * so the "install unknown apps" flow behaves exactly as the user expects.
 *
 * Installing an APK requires the user to grant this app the "install unknown apps"
 * permission on API 26+. That grant cannot be requested at runtime: the only way is
 * to send the user to the settings screen, which [canInstallPackages] reports and
 * the UI offers to open.
 */
class ApkInstaller(private val context: Context) {

    private val downloadManager: DownloadManager?
        get() = ContextCompat.getSystemService(context, DownloadManager::class.java)

    /** True when the system will let PulsePad hand an APK to the installer. */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager
            .canRequestPackageInstalls()

    /** The settings screen where the user grants the install permission. */
    fun installPermissionIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Downloads [url] and launches the installer.
     *
     * Returns the downloaded file, or null if the download failed or timed out.
     * The caller shows progress from the system notification, so this only needs to
     * report the end state.
     */
    suspend fun downloadAndInstall(update: AvailableUpdate): File? {
        val manager = downloadManager
        if (manager == null) {
            AppLog.w(TAG, "No DownloadManager on this device; cannot self-update")
            return null
        }
        val destination = File(apkDirectory(), apkFileName(update))
        // A previous attempt may have left a partial file; DownloadManager refuses
        // to overwrite, so clear it first.
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle(update.releaseName)
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "$UPDATE_DIR/${apkFileName(update)}"
            )

        val downloadId = try {
            manager.enqueue(request)
        } catch (exception: IllegalArgumentException) {
            AppLog.w(TAG, "Download request rejected", exception)
            return null
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Download not permitted", securityException)
            return null
        }

        val finished = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            awaitDownload(manager, downloadId)
        } ?: return null
        if (!finished) {
            AppLog.w(TAG, "Download did not complete in time")
            return null
        }
        return destination.takeIf { it.isFile }
    }

    /** Launches the system package installer for a downloaded APK. */
    fun install(file: File): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, authority(), file)
        } catch (exception: IllegalArgumentException) {
            AppLog.w(TAG, "APK is not inside the shared file provider path", exception)
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (exception: android.content.ActivityNotFoundException) {
            AppLog.w(TAG, "No package installer on this device", exception)
            false
        }
    }

    /**
     * Suspends until DownloadManager reports the download finished.
     *
     * The receiver is registered for exactly one download and unregistered on
     * completion, so nothing leaks if the coroutine is cancelled.
     */
    private suspend fun awaitDownload(
        manager: DownloadManager,
        downloadId: Long
    ): Boolean? = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                runCatching { receiverContext.unregisterReceiver(this) }
                val succeeded = runCatching {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    manager.query(query).use { cursor ->
                        cursor.moveToFirst() && cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        ) == DownloadManager.STATUS_SUCCESSFUL
                    }
                }.getOrDefault(false)
                if (continuation.isActive) continuation.resume(succeeded)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        continuation.invokeOnCancellation {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun apkDirectory(): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_DIR)
            .apply { mkdirs() }

    private fun apkFileName(update: AvailableUpdate): String =
        "pulsepad-${update.version}.apk"

    private fun authority(): String = "${context.packageName}.fileprovider"

    private companion object {
        const val TAG = "ApkInstaller"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L

        /** Must match the `path` in res/xml/file_paths.xml. */
        const val UPDATE_DIR = "updates"
    }
}
