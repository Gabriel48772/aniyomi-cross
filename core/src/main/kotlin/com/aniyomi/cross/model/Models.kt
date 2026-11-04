package com.aniyomi.cross.model

import kotlinx.serialization.Serializable

enum class ContentType {
    ANIME,
    MANGA
}

@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String,
    val type: ContentType,
    val format: String = "JSON" // JSON o PB
)

@Serializable
data class ExtensionMeta(
    val name: String,
    val pkg: String,
    val version: String,
    val lang: String,
    val apkUrl: String,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val type: ContentType = ContentType.MANGA,
    val sources: List<SourceSummary> = emptyList(),
    var isInstalled: Boolean = false
)

@Serializable
data class SourceSummary(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String? = null
)

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val coverUrl: String,
    val type: ContentType,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: String = "Desconocido",
    val author: String = "",
    val sourceName: String = ""
)

@Serializable
data class EpisodeOrChapter(
    val id: String,
    val name: String,
    val number: Float,
    val url: String,
    val uploadDate: String = "",
    val scanlator: String = ""
)

@Serializable
data class VideoSource(
    val quality: String, // ej. "1080p", "720p", "Auto"
    val videoUrl: String,
    val format: String = "HLS", // "HLS" (.m3u8) o "MP4"
    val headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null
)

@Serializable
data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class DownloadTask(
    val id: String,
    val mediaTitle: String,
    val episodeOrChapterName: String,
    val url: String,
    val type: ContentType,
    var progress: Float = 0f,
    var isCompleted: Boolean = false,
    var localPath: String = "",
    var isFailed: Boolean = false,
    var errorMessage: String = ""
)
