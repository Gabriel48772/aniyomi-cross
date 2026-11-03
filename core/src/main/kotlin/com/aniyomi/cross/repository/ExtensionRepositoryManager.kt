package com.aniyomi.cross.repository

import com.aniyomi.cross.model.ContentType
import com.aniyomi.cross.model.ExtensionMeta
import com.aniyomi.cross.model.ExtensionRepo
import com.aniyomi.cross.model.SourceSummary
import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ExtensionRepositoryManager {

    val defaultRepositories = listOf(
        // Repositorios de Anime
        ExtensionRepo(
            name = "Yuzono Anime Repo",
            url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
            type = ContentType.ANIME
        ),
        ExtensionRepo(
            name = "SalmanBappi Anime Repo",
            url = "https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json",
            type = ContentType.ANIME
        ),
        // Repositorios de Manga / Manhwa
        ExtensionRepo(
            name = "Keiyoushi Manga Repo",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json",
            type = ContentType.MANGA
        ),
        ExtensionRepo(
            name = "Suwayomi Manga Repo",
            url = "https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json",
            type = ContentType.MANGA
        )
    )

    private val cachedExtensions = mutableListOf<ExtensionMeta>()

    suspend fun fetchAllExtensions(forceRefresh: Boolean = false): List<ExtensionMeta> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedExtensions.isNotEmpty()) {
            return@withContext cachedExtensions
        }

        val results = mutableListOf<ExtensionMeta>()
        for (repo in defaultRepositories) {
            try {
                val jsonString = NetworkClient.get(repo.url)
                val parsed = parseRepositoryJson(jsonString, repo)
                results.addAll(parsed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cachedExtensions.clear()
        cachedExtensions.addAll(results)
        results
    }

    private fun parseRepositoryJson(jsonString: String, repo: ExtensionRepo): List<ExtensionMeta> {
        val list = mutableListOf<ExtensionMeta>()
        val trimmed = jsonString.trim()

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("extensionList")) {
                val extListObj = root.getJSONObject("extensionList")
                if (extListObj.has("extensions")) {
                    val array = extListObj.getJSONArray("extensions")
                    for (i in 0 until array.length()) {
                        parseKeiyoushiExtension(array.getJSONObject(i), repo.type)?.let { list.add(it) }
                    }
                }
            }
        } else if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                parseStandardExtension(item, repo.type, repo.url)?.let { list.add(it) }
            }
        }

        return list
    }

    private fun parseKeiyoushiExtension(item: JSONObject, type: ContentType): ExtensionMeta? {
        return try {
            val name = item.optString("name", "Desconocido")
            val pkg = item.optString("packageName", item.optString("pkg", ""))
            val version = item.optString("version", "1.0")
            val isNsfw = item.optInt("nsfw", 0) == 1

            val resources = item.optJSONObject("resources")
            val apkUrl = resources?.optString("apkUrl") ?: ""
            val iconUrl = resources?.optString("iconUrl")

            var detectedLang = item.optString("lang", "")
            if (detectedLang.isEmpty()) {
                if (pkg.contains(".es.")) detectedLang = "es"
                else if (pkg.contains(".en.")) detectedLang = "en"
            }

            val sources = mutableListOf<SourceSummary>()
            val sourcesArray = item.optJSONArray("sources")
            if (sourcesArray != null) {
                for (j in 0 until sourcesArray.length()) {
                    val s = sourcesArray.getJSONObject(j)
                    val sLang = s.optString("language", s.optString("lang", "all"))
                    if (detectedLang.isEmpty() && sLang != "all") {
                        detectedLang = sLang
                    }
                    sources.add(
                        SourceSummary(
                            id = s.optString("id", j.toString()),
                            name = s.optString("name", name),
                            lang = sLang,
                            baseUrl = s.optString("homeUrl", s.optString("baseUrl", null))
                        )
                    )
                }
            }
            if (detectedLang.isEmpty()) detectedLang = "all"

            ExtensionMeta(
                name = name,
                pkg = pkg,
                version = version,
                lang = detectedLang,
                apkUrl = apkUrl,
                iconUrl = iconUrl,
                isNsfw = isNsfw,
                type = type,
                sources = sources
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStandardExtension(item: JSONObject, type: ContentType, repoUrl: String): ExtensionMeta? {
        return try {
            val name = item.optString("name", "Desconocido")
            val pkg = item.optString("pkg", "")
            val apk = item.optString("apk", "")
            val version = item.optString("version", "1.0")
            val lang = item.optString("lang", "all")
            val isNsfw = item.optInt("nsfw", 0) == 1

            // Construir URL completa del APK
            val apkUrl = if (apk.startsWith("http")) {
                apk
            } else {
                val base = repoUrl.substringBeforeLast("/")
                "$base/apk/$apk"
            }

            val iconUrl = item.optString("icon", "").let { if (it.isNotEmpty()) it else null }

            val sources = mutableListOf<SourceSummary>()
            val sourcesArray = item.optJSONArray("sources")
            if (sourcesArray != null) {
                for (j in 0 until sourcesArray.length()) {
                    val s = sourcesArray.getJSONObject(j)
                    sources.add(
                        SourceSummary(
                            id = s.optString("id", j.toString()),
                            name = s.optString("name", name),
                            lang = s.optString("lang", lang),
                            baseUrl = s.optString("baseUrl", null)
                        )
                    )
                }
            }

            ExtensionMeta(
                name = name,
                pkg = pkg,
                version = version,
                lang = lang,
                apkUrl = apkUrl,
                iconUrl = iconUrl,
                isNsfw = isNsfw,
                type = type,
                sources = sources
            )
        } catch (e: Exception) {
            null
        }
    }

    fun filterExtensions(
        list: List<ExtensionMeta>,
        query: String = "",
        type: ContentType? = null,
        language: String? = null
    ): List<ExtensionMeta> {
        return list.filter { ext ->
            val matchesQuery = query.isEmpty() || ext.name.contains(query, ignoreCase = true) || ext.pkg.contains(query, ignoreCase = true)
            val matchesType = type == null || ext.type == type
            val matchesLang = language == null || language == "all" || ext.lang.equals(language, ignoreCase = true)
            matchesQuery && matchesType && matchesLang
        }
    }
}
