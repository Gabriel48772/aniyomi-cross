package com.aniyomi.cross.downloader

import com.aniyomi.cross.model.*
import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class OfflineMedia(
    val id: String,
    val title: String,
    val subTitle: String,
    val type: ContentType,
    val localFileOrDir: File,
    val pageCountOrSize: String,
    val dateAdded: String
)

class OfflineStorageManager(val baseDir: File) {

    init {
        if (!baseDir.exists()) baseDir.mkdirs()
        File(baseDir, "Manga").mkdirs()
        File(baseDir, "Anime").mkdirs()
    }

    suspend fun downloadAnimeEpisode(
        media: MediaItem,
        episode: EpisodeOrChapter,
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (videoUrl.isBlank()) return@withContext null
            val sanitizedMedia = sanitize(media.title)
            val sanitizedEp = sanitize(episode.name)
            val mediaDir = File(baseDir, "Anime/$sanitizedMedia")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val targetFile = File(mediaDir, "$sanitizedEp.mp4")

            var directUrl = videoUrl.trim()
            if (directUrl.startsWith("//")) directUrl = "https:$directUrl"
            val effectiveHeaders = headers.toMutableMap()

            // Si la URL no es un stream directo .mp4 o .m3u8, resolver el enlace multimedia subyacente
            if (!directUrl.contains(".mp4", ignoreCase = true) && !directUrl.contains(".m3u8", ignoreCase = true)) {
                val resolved = resolveDirectMediaUrl(directUrl, headers)
                if (resolved != null) {
                    directUrl = resolved.first
                    effectiveHeaders.putAll(resolved.second)
                }
            }

            val success = if (directUrl.contains(".m3u8", ignoreCase = true)) {
                downloadHlsToMp4(directUrl, targetFile, effectiveHeaders, onProgress)
            } else {
                NetworkClient.downloadToFile(directUrl, targetFile, effectiveHeaders, onProgress)
            }

            if (success && targetFile.exists() && targetFile.length() > 50_000) {
                // Verificar que no sea una página de error HTML guardada por accidente
                val isHtml = try {
                    targetFile.inputStream().use { stream ->
                        val buf = ByteArray(120)
                        val read = stream.read(buf)
                        if (read > 0) {
                            val header = String(buf, 0, read).lowercase()
                            header.contains("<html") || header.contains("<!doctype")
                        } else false
                    }
                } catch (e: Throwable) {
                    false
                }

                if (isHtml) {
                    targetFile.delete()
                    return@withContext null
                }
                targetFile
            } else {
                if (targetFile.exists() && targetFile.length() <= 50_000) {
                    targetFile.delete()
                }
                null
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private suspend fun resolveDirectMediaUrl(
        videoUrl: String,
        headers: Map<String, String>
    ): Pair<String, Map<String, String>>? = withContext(Dispatchers.IO) {
        val trimmed = videoUrl.trim()
        val defaultHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        defaultHeaders.putAll(headers)

        try {
            // Caso 1: Enlace a Mediafire
            if (trimmed.contains("mediafire.com/file/", ignoreCase = true)) {
                val mfHtml = NetworkClient.get(trimmed, defaultHeaders)
                val mfRegex = Regex("""href=["'](https?://download[0-9]*\.mediafire\.com/[^"']+)["']""")
                val match = mfRegex.find(mfHtml) ?: Regex("""id="downloadButton"[^>]*href=["']([^"']+)["']""").find(mfHtml)
                if (match != null) {
                    val direct = match.groupValues[1]
                    return@withContext Pair(direct, defaultHeaders)
                }
            }

            // Caso 2: Enlace a Voe (voe.sx/e/...)
            if (trimmed.contains("voe.sx", ignoreCase = true) || trimmed.contains("/e/", ignoreCase = true)) {
                val voeHeaders = defaultHeaders.toMutableMap()
                voeHeaders["Referer"] = trimmed
                val voeHtml = NetworkClient.get(trimmed, voeHeaders)
                val hlsMatch = Regex("""'hls':\s*'([^']+)'""").find(voeHtml)
                    ?: Regex(""""file":\s*"([^"]+\.m3u8[^"]*)"""").find(voeHtml)
                    ?: Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(voeHtml)
                if (hlsMatch != null) {
                    return@withContext Pair(hlsMatch.groupValues[1], voeHeaders)
                }
            }

            // Caso 3: Reproductor JKanime o servidor embed genérico
            val embedHeaders = defaultHeaders.toMutableMap()
            if (!embedHeaders.containsKey("Referer")) {
                embedHeaders["Referer"] = "https://jkanime.net/"
            }
            val pageHtml = NetworkClient.get(trimmed, embedHeaders)

            val srcMatch = Regex("""<source\s+src=["']([^"']+)["']""").find(pageHtml)
                ?: Regex("""sources:\s*\[\{\s*file:\s*["']([^"']+)["']""").find(pageHtml)
                ?: Regex("""file:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(pageHtml)
                ?: Regex("""(https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*)""").find(pageHtml)

            if (srcMatch != null) {
                val streamUrl = srcMatch.groupValues[1]
                if (streamUrl.startsWith("http")) {
                    return@withContext Pair(streamUrl, embedHeaders)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        null
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            relativeOrAbsolute
        } else {
            try {
                java.net.URI(baseUrl).resolve(relativeOrAbsolute).toString()
            } catch (e: Exception) {
                val baseTrim = baseUrl.substringBeforeLast("/")
                "$baseTrim/${relativeOrAbsolute.removePrefix("/")}"
            }
        }
    }

    private suspend fun downloadHlsToMp4(
        m3u8Url: String,
        targetFile: File,
        headers: Map<String, String>,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqHeaders = headers.toMutableMap()
            if (!reqHeaders.containsKey("User-Agent")) {
                reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }
            if (!reqHeaders.containsKey("Referer")) {
                val host = try { java.net.URI(m3u8Url).host } catch (e: Exception) { "" }
                reqHeaders["Referer"] = if (!host.isNullOrEmpty()) "https://$host/" else "https://jkanime.net/"
            }
            val m3u8Text = NetworkClient.get(m3u8Url, reqHeaders)

            var mediaPlaylistUrl = m3u8Url
            var playlistContent = m3u8Text

            // Si es un Master Playlist que contiene enlaces a diferentes calidades
            if (m3u8Text.contains("#EXT-X-STREAM-INF")) {
                val lines = m3u8Text.lines().map { it.trim() }
                for (i in lines.indices) {
                    if (lines[i].startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val sub = lines[i + 1]
                        if (!sub.startsWith("#") && sub.isNotEmpty()) {
                            mediaPlaylistUrl = resolveUrl(m3u8Url, sub)
                            playlistContent = NetworkClient.get(mediaPlaylistUrl, reqHeaders)
                            break
                        }
                    }
                }
            }

            val segmentLines = playlistContent.lines().map { it.trim() }
            val segments = mutableListOf<String>()
            for (line in segmentLines) {
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    segments.add(resolveUrl(mediaPlaylistUrl, line))
                }
            }

            if (segments.isEmpty()) return@withContext false

            val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")
            if (tempFile.exists()) tempFile.delete()

            tempFile.outputStream().use { out ->
                for ((idx, segUrl) in segments.withIndex()) {
                    var bytes = NetworkClient.getBytes(segUrl, reqHeaders)
                    if (bytes == null || bytes.isEmpty()) {
                        kotlinx.coroutines.delay(350)
                        bytes = NetworkClient.getBytes(segUrl, reqHeaders)
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        out.write(bytes)
                    }
                    onProgress((idx + 1).toFloat() / segments.size.toFloat())
                }
                out.flush()
            }

            if (tempFile.exists() && tempFile.length() > 50_000) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                return@withContext true
            }
            if (tempFile.exists()) tempFile.delete()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadMangaChapter(
        media: MediaItem,
        chapter: EpisodeOrChapter,
        pages: List<MangaPage>,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val sanitizedMedia = sanitize(media.title)
        val sanitizedCh = sanitize(chapter.name)
        val chapterDir = File(baseDir, "Manga/$sanitizedMedia/$sanitizedCh")
        chapterDir.mkdirs()

        var downloadedCount = 0
        for ((idx, page) in pages.withIndex()) {
            val pageNum = (idx + 1).toString().padStart(3, '0')
            val pageFile = File(chapterDir, "page_$pageNum.jpg")
            val ok = NetworkClient.downloadToFile(page.imageUrl, pageFile)
            if (ok) downloadedCount++
            onProgress(downloadedCount.toFloat() / pages.size.toFloat())
        }
        if (chapterDir.exists() && chapterDir.listFiles()?.isNotEmpty() == true) chapterDir else null
    }

    fun getDownloadedMangas(): List<OfflineMedia> {
        val list = mutableListOf<OfflineMedia>()
        val mangaDir = File(baseDir, "Manga")
        if (!mangaDir.exists()) return emptyList()

        val seriesFolders = mangaDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (seriesFolder in seriesFolders) {
            val chapterFolders = seriesFolder.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (chFolder in chapterFolders) {
                val pages = chFolder.listFiles()?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") } ?: emptyList()
                if (pages.isNotEmpty()) {
                    list.add(
                        OfflineMedia(
                            id = chFolder.absolutePath.hashCode().toString(),
                            title = seriesFolder.name.replace("_", " "),
                            subTitle = chFolder.name.replace("_", " "),
                            type = ContentType.MANGA,
                            localFileOrDir = chFolder,
                            pageCountOrSize = "${pages.size} páginas",
                            dateAdded = "Guardado local"
                        )
                    )
                }
            }
        }
        return list
    }

    fun getDownloadedAnimes(): List<OfflineMedia> {
        val list = mutableListOf<OfflineMedia>()
        val animeDir = File(baseDir, "Anime")
        if (!animeDir.exists()) return emptyList()

        val seriesFolders = animeDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (seriesFolder in seriesFolders) {
            val videoFiles = seriesFolder.listFiles()?.filter { it.extension.lowercase() in listOf("mp4", "mkv", "webm") } ?: emptyList()
            for (vFile in videoFiles) {
                val sizeMb = vFile.length() / (1024 * 1024)
                list.add(
                    OfflineMedia(
                        id = vFile.absolutePath.hashCode().toString(),
                        title = seriesFolder.name.replace("_", " "),
                        subTitle = vFile.nameWithoutExtension.replace("_", " "),
                        type = ContentType.ANIME,
                        localFileOrDir = vFile,
                        pageCountOrSize = "$sizeMb MB",
                        dateAdded = "Guardado local"
                    )
                )
            }
        }
        return list
    }

    fun deleteOfflineItem(item: OfflineMedia): Boolean {
        return try {
            if (item.localFileOrDir.isDirectory) {
                item.localFileOrDir.deleteRecursively()
            } else {
                item.localFileOrDir.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_ -]"), "").trim().replace(" ", "_")
    }
}
