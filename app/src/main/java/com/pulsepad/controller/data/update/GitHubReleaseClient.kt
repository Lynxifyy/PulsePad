package com.pulsepad.controller.data.update

import com.pulsepad.controller.domain.engine.GitHubRelease
import com.pulsepad.controller.domain.engine.ReleaseParser
import com.pulsepad.controller.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches release metadata from the GitHub REST API.
 *
 * Deliberately built on [HttpURLConnection] rather than OkHttp: it is part of the
 * platform, so the updater adds no third-party dependency to an app that is meant
 * to stay installable on devices with nothing else available. The call is
 * read-only and unauthenticated, so no token ships in the APK.
 */
class GitHubReleaseClient(
    private val repositorySlug: String = DEFAULT_REPOSITORY
) {

    /**
     * The newest releases, newest first.
     *
     * Returns an empty list rather than throwing: a device with no network, or
     * GitHub being unreachable, is a normal state for an app that also works
     * fully offline, and the caller shows "could not check" rather than a crash.
     */
    suspend fun fetchReleases(limit: Int = DEFAULT_LIMIT): List<GitHubRelease> =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/repos/$repositorySlug/releases?per_page=$limit"
            try {
                val payload = get(url)
                ReleaseParser.parseReleases(payload)
            } catch (io: IOException) {
                AppLog.w(TAG, "Could not reach GitHub for an update check", io)
                emptyList()
            } catch (security: SecurityException) {
                AppLog.w(TAG, "Update check blocked", security)
                emptyList()
            }
        }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub returned HTTP $status")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "UpdateClient"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val DEFAULT_LIMIT = 20

        /** Overridable so a fork can point the updater at its own releases. */
        const val DEFAULT_REPOSITORY = "Lynxifyy/PulsePad"
    }
}
