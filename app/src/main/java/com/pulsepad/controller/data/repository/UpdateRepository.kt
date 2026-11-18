package com.pulsepad.controller.data.repository

import com.pulsepad.controller.BuildConfig
import com.pulsepad.controller.data.update.ApkInstaller
import com.pulsepad.controller.data.update.GitHubReleaseClient
import com.pulsepad.controller.domain.engine.AvailableUpdate
import com.pulsepad.controller.domain.engine.ReleaseParser
import com.pulsepad.controller.domain.model.AppVersion
import com.pulsepad.controller.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a newer release exists and drives the download and install.
 *
 * The version the app compares against is its own `versionName`, so a build that
 * was sideloaded from a branch simply sees itself as older or newer by the same
 * rule a user would apply by eye.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val client: GitHubReleaseClient,
    private val installer: ApkInstaller
) {

    /** The running build's version, or a zero version if it cannot be parsed. */
    val currentVersion: AppVersion
        get() = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)

    /**
     * The newest release newer than the running build, or null when up to date.
     *
     * A null result is also returned when the check could not be performed; the
     * caller distinguishes the two by whether an error was reported.
     */
    suspend fun checkForUpdate(allowPrerelease: Boolean = false) =
        ReleaseParser.findUpdate(
            releases = client.fetchReleases(),
            current = currentVersion,
            allowPrerelease = allowPrerelease
        )

    /** True when the platform will accept an install handed to it by PulsePad. */
    fun canSelfInstall(): Boolean = installer.canInstallPackages()

    /** Opens the "install unknown apps" settings page for PulsePad. */
    fun installPermissionIntent() = installer.installPermissionIntent()

    /**
     * Downloads the update and launches the installer.
     *
     * Returns false when the download failed, when the device has no installer, or
     * when the file could not be shared with it, so the UI can say so instead of
     * appearing to do nothing.
     */
    suspend fun downloadAndInstall(update: AvailableUpdate): Boolean {
        if (!canSelfInstall()) {
            AppLog.w(TAG, "Install permission not granted; cannot self-install")
            return false
        }
        val file = installer.downloadAndInstall(update) ?: return false
        return installer.install(file)
    }

    private companion object {
        const val TAG = "UpdateRepository"
    }
}
