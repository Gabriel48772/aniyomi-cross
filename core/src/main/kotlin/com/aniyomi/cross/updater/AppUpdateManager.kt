package com.aniyomi.cross.updater

import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object AppUpdateManager {

    var githubRepoOwner = "Gabriel48772"
    var githubRepoName = "aniyomi-cross"
    const val CURRENT_VERSION = "1.2.1"

    private var dismissedVersion: String? = null

    private val localVersionFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".aniyomicross")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "installed_version.txt")
    }

    fun getInstalledVersion(): String {
        return try {
            if (localVersionFile.exists()) {
                val v = localVersionFile.readText().trim()
                if (v.isNotEmpty()) v else CURRENT_VERSION
            } else {
                CURRENT_VERSION
            }
        } catch (e: Exception) {
            CURRENT_VERSION
        }
    }

    fun markVersionInstalled(version: String) {
        try {
            localVersionFile.writeText(version.trim())
            dismissedVersion = version.trim()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissVersion(version: String) {
        dismissedVersion = version.trim()
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val apkDownloadUrl: String?,
        val jarDownloadUrl: String?,
        val releaseNotes: String
    )

    suspend fun checkLatestRelease(): UpdateInfo = withContext(Dispatchers.IO) {
        val installedVer = getInstalledVersion()
        // 1. Intentar mediante version.json directo de GitHub
        try {
            val rawVersionUrl = "https://raw.githubusercontent.com/$githubRepoOwner/$githubRepoName/main/version.json"
            val jsonStr = NetworkClient.get(rawVersionUrl)
            val root = JSONObject(jsonStr)
            val ver = root.optString("version", installedVer)
            val apk = root.optString("apkUrl", "")
            val jar = root.optString("jarUrl", "")
            val notes = root.optString("releaseNotes", "Mejoras y correcciones generales.")

            val isNewer = isVersionNewer(ver, installedVer)
            val isDismissed = (dismissedVersion != null && dismissedVersion == ver)

            if (isNewer && !isDismissed) {
                return@withContext UpdateInfo(
                    hasUpdate = true,
                    latestVersion = ver,
                    apkDownloadUrl = apk.ifEmpty { null },
                    jarDownloadUrl = jar.ifEmpty { null },
                    releaseNotes = notes
                )
            } else {
                return@withContext UpdateInfo(false, installedVer, null, null, "")
            }
        } catch (e: Exception) {
            // Intentar fallback
        }

        // 2. Fallback: Releases API de GitHub
        try {
            val apiUrl = "https://api.github.com/repos/$githubRepoOwner/$githubRepoName/releases/latest"
            val jsonStr = NetworkClient.get(apiUrl, mapOf("Accept" to "application/vnd.github.v3+json"))
            val root = JSONObject(jsonStr)

            val tagName = root.optString("tag_name", installedVer).removePrefix("v")
            val body = root.optString("body", "Nueva versión disponible.")

            var apkUrl: String? = null
            var jarUrl: String? = null

            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = downloadUrl
                    } else if (name.endsWith(".jar", ignoreCase = true)) {
                        jarUrl = downloadUrl
                    }
                }
            }

            val isNewer = isVersionNewer(tagName, installedVer)
            val isDismissed = (dismissedVersion != null && dismissedVersion == tagName)

            UpdateInfo(
                hasUpdate = isNewer && !isDismissed,
                latestVersion = tagName,
                apkDownloadUrl = apkUrl,
                jarDownloadUrl = jarUrl,
                releaseNotes = body
            )
        } catch (e: Exception) {
            UpdateInfo(false, installedVer, null, null, "")
        }
    }

    fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest == current) return false
        val lParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadUpdate(downloadUrl: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            NetworkClient.downloadToFile(downloadUrl, destinationFile, onProgress)
        }
    }
}
