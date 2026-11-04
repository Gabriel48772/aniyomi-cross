package com.aniyomi.cross.updater

import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object AppUpdateManager {

    var githubRepoOwner = "Gabriel48772"
    var githubRepoName = "aniyomi-cross"
    const val CURRENT_VERSION = "1.0.0"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val apkDownloadUrl: String?,
        val jarDownloadUrl: String?,
        val releaseNotes: String
    )

    suspend fun checkLatestRelease(): UpdateInfo = withContext(Dispatchers.IO) {
        // Primero intentar mediante version.json directo de GitHub (actualización instantánea con cada commit)
        try {
            val rawVersionUrl = "https://raw.githubusercontent.com/$githubRepoOwner/$githubRepoName/main/version.json"
            val jsonStr = NetworkClient.get(rawVersionUrl)
            val root = JSONObject(jsonStr)
            val ver = root.optString("version", "1.0.0")
            val apk = root.optString("apkUrl", "")
            val jar = root.optString("jarUrl", "")
            val notes = root.optString("releaseNotes", "Mejoras y correcciones generales.")

            val isNewer = isVersionNewer(ver, CURRENT_VERSION)
            if (isNewer) {
                return@withContext UpdateInfo(
                    hasUpdate = true,
                    latestVersion = ver,
                    apkDownloadUrl = apk.ifEmpty { null },
                    jarDownloadUrl = jar.ifEmpty { null },
                    releaseNotes = notes
                )
            }
        } catch (e: Exception) {
            // Intentar con Releases API
        }

        // Fallback: Releases API de GitHub
        try {
            val apiUrl = "https://api.github.com/repos/$githubRepoOwner/$githubRepoName/releases/latest"
            val jsonStr = NetworkClient.get(apiUrl, mapOf("Accept" to "application/vnd.github.v3+json"))
            val root = JSONObject(jsonStr)

            val tagName = root.optString("tag_name", "v1.0.0").removePrefix("v")
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

            val isNewer = isVersionNewer(tagName, CURRENT_VERSION)
            UpdateInfo(
                hasUpdate = isNewer,
                latestVersion = tagName,
                apkDownloadUrl = apkUrl,
                jarDownloadUrl = jarUrl,
                releaseNotes = body
            )
        } catch (e: Exception) {
            UpdateInfo(false, CURRENT_VERSION, null, null, "")
        }
    }

    fun isVersionNewer(latest: String, current: String): Boolean {
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
