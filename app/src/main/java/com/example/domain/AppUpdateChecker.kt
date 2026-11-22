package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String, val releasePageUrl: String) : UpdateCheckResult
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseTitle: String,
        val releasePageUrl: String,
        val apkDownloadUrl: String?
    ) : UpdateCheckResult
    data class NoReleaseYet(val currentVersion: String, val actionsPageUrl: String) : UpdateCheckResult
    data class Error(val message: String, val fallbackUrl: String) : UpdateCheckResult
}

/**
 * Play-Policy-compliant GitHub Release version checker.
 * Checks public GitHub Releases metadata over HTTPS and opens the browser download page via Intent.ACTION_VIEW
 * (never downloads or installs APKs inside the app, requiring zero restricted permissions).
 */
object AppUpdateChecker {

    const val REPO_OWNER = "muhammadasadtareen-byte"
    const val REPO_NAME = "Educators"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
    const val ACTIONS_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/actions"
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * Parses a version string such as "v2.10", "2.9", or "2.9.1" into integer segments.
     */
    fun parseVersionSegments(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        return cleaned.split(".")
            .mapNotNull { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() }
    }

    /**
     * Returns true strictly when [latestVersion] is newer than [currentVersion].
     */
    fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latestSegs = parseVersionSegments(latestVersion)
        val currentSegs = parseVersionSegments(currentVersion)
        if (latestSegs.isEmpty() || currentSegs.isEmpty()) return false
        val maxLen = maxOf(latestSegs.size, currentSegs.size)
        for (i in 0 until maxLen) {
            val l = latestSegs.getOrElse(i) { 0 }
            val c = currentSegs.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    suspend fun checkLatestRelease(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EducatorsResults-Android/$currentVersion")
            }
            val status = conn.responseCode
            if (status == 404) {
                return@withContext UpdateCheckResult.NoReleaseYet(
                    currentVersion = currentVersion,
                    actionsPageUrl = ACTIONS_PAGE_URL
                )
            }
            if (status !in 200..299) {
                return@withContext UpdateCheckResult.Error(
                    message = "Could not reach GitHub (HTTP $status).",
                    fallbackUrl = RELEASES_PAGE_URL
                )
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", tagName).ifBlank { tagName }
            val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL).ifBlank { RELEASES_PAGE_URL }

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                        if (apkUrl != null) break
                    }
                }
            }

            val cleanLatest = tagName.removePrefix("v").removePrefix("V")
            if (cleanLatest.isNotBlank() && isNewerVersion(cleanLatest, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = cleanLatest,
                    releaseTitle = releaseName,
                    releasePageUrl = htmlUrl,
                    apkDownloadUrl = apkUrl
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    releasePageUrl = htmlUrl
                )
            }
        } catch (_: Exception) {
            UpdateCheckResult.Error(
                message = "Unable to check updates right now. Check your internet connection.",
                fallbackUrl = RELEASES_PAGE_URL
            )
        } finally {
            conn?.disconnect()
        }
    }

    fun openExternalUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
