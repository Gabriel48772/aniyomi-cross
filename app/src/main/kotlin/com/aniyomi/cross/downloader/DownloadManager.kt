package com.aniyomi.cross.downloader

import android.content.Context
import android.os.Environment
import com.aniyomi.cross.model.ContentType
import com.aniyomi.cross.model.DownloadTask
import com.aniyomi.cross.model.EpisodeOrChapter
import com.aniyomi.cross.model.MediaItem
import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CrossDownloadManager(private val context: Context) {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val baseDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AniyomiCross")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun downloadEpisode(media: MediaItem, episode: EpisodeOrChapter, videoUrl: String) {
        val task = DownloadTask(
            id = "${media.id}_${episode.id}",
            mediaTitle = media.title,
            episodeOrChapterName = episode.name,
            url = videoUrl,
            type = ContentType.ANIME
        )

        _tasks.value = _tasks.value + task

        scope.launch {
            val sanitizedMedia = media.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val sanitizedEp = episode.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetFile = File(File(baseDir, "Anime/$sanitizedMedia"), "$sanitizedEp.mp4")

            val success = NetworkClient.downloadToFile(videoUrl, targetFile) { progress ->
                updateTaskProgress(task.id, progress)
            }

            if (success) {
                markCompleted(task.id, targetFile.absolutePath)
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

        _tasks.value = _tasks.value + task

        scope.launch {
            val sanitizedMedia = media.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val sanitizedCh = chapter.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val chapterDir = File(File(baseDir, "Manga/$sanitizedMedia"), sanitizedCh)
            chapterDir.mkdirs()

            var count = 0
            for ((idx, pUrl) in pageUrls.withIndex()) {
                val pageFile = File(chapterDir, "page_${idx + 1}.jpg")
                NetworkClient.downloadToFile(pUrl, pageFile)
                count++
                val prog = count.toFloat() / pageUrls.size.toFloat()
                updateTaskProgress(task.id, prog)
            }

            markCompleted(task.id, chapterDir.absolutePath)
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
