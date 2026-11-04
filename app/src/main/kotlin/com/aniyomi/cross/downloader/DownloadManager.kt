package com.aniyomi.cross.downloader

import android.content.Context
import android.os.Environment
import com.aniyomi.cross.model.ContentType
import com.aniyomi.cross.model.DownloadTask
import com.aniyomi.cross.model.EpisodeOrChapter
import com.aniyomi.cross.model.MangaPage
import com.aniyomi.cross.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CrossDownloadManager(
    private val context: Context,
    private val offlineStorage: OfflineStorageManager
) {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun downloadEpisode(media: MediaItem, episode: EpisodeOrChapter, videoUrl: String) {
        val task = DownloadTask(
            id = "${media.id}_${episode.id}",
            mediaTitle = media.title,
            episodeOrChapterName = episode.name,
            url = videoUrl,
            type = ContentType.ANIME
        )

        _tasks.value = _tasks.value.filter { it.id != task.id } + task

        scope.launch {
            val file = offlineStorage.downloadAnimeEpisode(media, episode, videoUrl) { progress ->
                updateTaskProgress(task.id, progress)
            }

            if (file != null && file.exists()) {
                markCompleted(task.id, file.absolutePath)
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                } catch (e: Exception) {}
            }
        }
    }

    fun downloadChapter(media: MediaItem, chapter: EpisodeOrChapter, pageUrls: List<String>) {
        val task = DownloadTask(
            id = "${media.id}_${chapter.id}",
            mediaTitle = media.title,
            episodeOrChapterName = chapter.name,
            url = chapter.url,
            type = ContentType.MANGA
        )

        _tasks.value = _tasks.value.filter { it.id != task.id } + task

        scope.launch {
            val pages = pageUrls.mapIndexed { idx, u -> MangaPage(idx + 1, u) }
            val dir = offlineStorage.downloadMangaChapter(media, chapter, pages) { progress ->
                updateTaskProgress(task.id, progress)
            }

            if (dir != null && dir.exists()) {
                markCompleted(task.id, dir.absolutePath)
            }
        }
    }

    private fun updateTaskProgress(id: String, progress: Float) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(progress = progress) else it
        }
    }

    private fun markCompleted(id: String, localPath: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(progress = 1.0f, isCompleted = true, localPath = localPath) else it
        }
    }
}
