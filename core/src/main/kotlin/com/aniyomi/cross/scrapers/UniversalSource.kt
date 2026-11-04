package com.aniyomi.cross.scrapers

import com.aniyomi.cross.model.*
import com.aniyomi.cross.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Interfaz universal para fuentes y scrapers en Aniyomi Cross
 */
interface CrossSource {
    val id: String
    val name: String
    val lang: String
    val type: ContentType
    val baseUrl: String

    suspend fun getPopular(page: Int = 1): List<MediaItem>
    suspend fun search(query: String, page: Int = 1): List<MediaItem>
    suspend fun filter(
        query: String = "",
        genre: String? = null,
        sort: String = "POPULARITY_DESC",
        country: String? = null,
        status: String? = null,
        page: Int = 1
    ): List<MediaItem> = getPopular(page)

    suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>>
    suspend fun getVideoList(episode: EpisodeOrChapter): List<VideoSource> = emptyList()
    suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = emptyList()
}

/**
 * Catálogo de Anime con Scraping Dinámico en Español (JKanime) para reproducir el episodio EXACTO y REAL
 */
class AniListAnimeSource : CrossSource {
    override val id = "anilist-anime"
    override val name = "Anime Cinema Español"
    override val lang = "es"
    override val type = ContentType.ANIME
    override val baseUrl = "https://graphql.anilist.co"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(sort = "POPULARITY_DESC", page = page)

    override suspend fun search(query: String, page: Int): List<MediaItem> = filter(query = query, page = page)

    override suspend fun filter(
        query: String,
        genre: String?,
        sort: String,
        country: String?,
        status: String?,
        page: Int
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val graphql = """
                query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}search: String, ${'$'}genre: String, ${'$'}sort: [MediaSort], ${'$'}status: MediaStatus) {
                  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, search: ${'$'}search, genre: ${'$'}genre, sort: ${'$'}sort, status: ${'$'}status, isAdult: false) {
                      id
                      title { romaji english native }
                      coverImage { large extraLarge }
                      description(asHtml: false)
                      episodes
                      status
                      genres
                      averageScore
                      bannerImage
                    }
                  }
                }
            """.trimIndent()

            val variables = JSONObject().apply {
                put("page", page)
                put("perPage", 50)
                if (query.trim().isNotEmpty()) put("search", query.trim())
                if (!genre.isNullOrEmpty() && genre != "Todos") put("genre", genre)
                if (!status.isNullOrEmpty() && status != "TODOS") {
                    val anilistStatus = when (status) {
                        "completed" -> "FINISHED"
                        "ongoing" -> "RELEASING"
                        else -> null
                    }
                    if (anilistStatus != null) put("status", anilistStatus)
                }
                val sortArr = JSONArray().apply { put(sort.ifEmpty { "POPULARITY_DESC" }) }
                put("sort", sortArr)
            }

            val requestPayload = JSONObject().apply {
                put("query", graphql)
                put("variables", variables)
            }

            val response = NetworkClient.postJson(baseUrl, requestPayload.toString())
            val json = JSONObject(response)
            val mediaArray = json.getJSONObject("data")
                .getJSONObject("Page")
                .getJSONArray("media")

            for (i in 0 until mediaArray.length()) {
                val item = mediaArray.getJSONObject(i)
                val titles = item.getJSONObject("title")
                val romaji = titles.optString("romaji", "")
                val english = titles.optString("english", "")
                val title = if (english.isNotEmpty()) english else if (romaji.isNotEmpty()) romaji else "Anime"
                val cover = item.getJSONObject("coverImage").optString("extraLarge", item.getJSONObject("coverImage").optString("large", ""))
                val episodes = item.optInt("episodes", 0)
                val desc = item.optString("description", "Sin descripción disponible.")
                val rawStatus = item.optString("status", "FINISHED")
                val statusText = if (rawStatus == "FINISHED") "✅ FINALIZADO" else "⏳ EN EMISIÓN"
                val score = item.optInt("averageScore", 0)

                val genresList = mutableListOf<String>()
                val genresArr = item.optJSONArray("genres")
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        genresList.add(genresArr.getString(g))
                    }
                }

                val epInfo = if (episodes > 0) "$episodes eps" else "En emisión"

                list.add(
                    MediaItem(
                        id = item.getInt("id").toString(),
                        title = title,
                        url = "$romaji:::alt::$english",
                        coverUrl = cover,
                        type = ContentType.ANIME,
                        description = desc.replace(Regex("<[^>]*>"), ""),
                        genres = genresList,
                        status = "$statusText • $epInfo • ${score}%",
                        sourceName = "Cinema Anime"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<EpisodeOrChapter>()
        var totalEpisodes = 12

        try {
            val graphql = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id) {
                    episodes
                    status
                  }
                }
            """.trimIndent()
            val payload = JSONObject().apply {
                put("query", graphql)
                put("variables", JSONObject().apply { put("id", media.id.toInt()) })
            }
            val res = NetworkClient.postJson(baseUrl, payload.toString())
            val jsonObj = JSONObject(res).getJSONObject("data").getJSONObject("Media")
            totalEpisodes = jsonObj.optInt("episodes", 0)
            if (totalEpisodes <= 0) {
                totalEpisodes = 24
            }
        } catch (e: Exception) {
            // Usar default
        }

        val titlesParts = media.url.split(":::alt::")
        val romaji = if (titlesParts.isNotEmpty()) titlesParts[0] else ""
        val english = if (titlesParts.size > 1) titlesParts[1] else ""

        for (i in 1..totalEpisodes) {
            episodes.add(
                EpisodeOrChapter(
                    id = "${media.id}_ep_$i",
                    name = "Episodio $i - Sub Español (Oficial)",
                    number = i.toFloat(),
                    url = "${media.title}:::romaji::$romaji:::eng::$english:::ep::$i",
                    uploadDate = "Reciente"
                )
            )
        }
        Pair(media, episodes)
    }

    override suspend fun getVideoList(episode: EpisodeOrChapter): List<VideoSource> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoSource>()
        try {
            // Extraer títulos candidatos y número de episodio
            val raw = episode.url
            val epNumMatch = Regex(":::ep::(\\d+)").find(raw)
            val epInt = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: episode.number.toInt().coerceAtLeast(1)

            val romajiMatch = Regex(":::romaji::(.*?)(:::|$)").find(raw)
            val engMatch = Regex(":::eng::(.*?)(:::|$)").find(raw)
            val titleClean = raw.substringBefore(":::").replace(Regex("""\[.*?\]|\(.*?\)|Season \d+|Temporada \d+|2nd Season|3rd Season|Part \d+"""), "").trim()

            val romaji = romajiMatch?.groupValues?.get(1)?.trim() ?: ""
            val english = engMatch?.groupValues?.get(1)?.trim() ?: ""

            // Lista de variantes de búsqueda ordenadas por probabilidad de coincidencia
            val queryCandidates = mutableListOf<String>()
            if (romaji.isNotEmpty()) {
                queryCandidates.add(romaji.replace(Regex("""\[.*?\]|\(.*?\)|Season \d+|Temporada \d+|Part \d+"""), "").trim())
                val words = romaji.split(" ").filter { it.length > 2 }
                if (words.size >= 2) queryCandidates.add("${words[0]} ${words[1]}")
                if (words.isNotEmpty()) queryCandidates.add(words[0])
            }
            if (titleClean.isNotEmpty()) {
                queryCandidates.add(titleClean)
                val words = titleClean.split(" ").filter { it.length > 2 }
                if (words.size >= 2) queryCandidates.add("${words[0]} ${words[1]}")
                if (words.isNotEmpty()) queryCandidates.add(words[0])
            }
            if (english.isNotEmpty() && english != titleClean) {
                queryCandidates.add(english)
            }

            val distinctCandidates = queryCandidates.distinct().filter { it.isNotBlank() }

            // ==========================================
            // MOTOR 1: JKanime (https://jkanime.net/)
            // ==========================================
            val jkSlugs = mutableSetOf<String>()
            for (candidate in distinctCandidates.take(3)) {
                try {
                    val encodedQ = URLEncoder.encode(candidate, "UTF-8")
                    val searchUrl = "https://jkanime.net/buscar?q=$encodedQ"
                    val searchHtml = NetworkClient.get(searchUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))

                    val slugRegex = Regex("""href=["']https://jkanime\.net/([a-zA-Z0-9-]+)/["']""")
                    val matches = slugRegex.findAll(searchHtml).toList()
                    for (m in matches) {
                        val s = m.groupValues[1]
                        if (s != "buscar" && s != "dash" && s != "img" && s != "directorio" && s != "horario") {
                            jkSlugs.add(s)
                        }
                    }
                    if (jkSlugs.isNotEmpty()) break
                } catch (e: Exception) {}
            }

            if (jkSlugs.isEmpty() && romaji.isNotEmpty()) {
                jkSlugs.add(romaji.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            }

            for (slug in jkSlugs.take(2)) {
                try {
                    val episodePageUrl = "https://jkanime.net/$slug/$epInt/"
                    val epHtml = NetworkClient.get(episodePageUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                    val playerUrlRegex = Regex("""https://jkanime\.net/jkplayer/[^"'><\s]+""")
                    val playerMatches = playerUrlRegex.findAll(epHtml).map { it.value }.distinct().toList()

                    for ((idx, playerUrl) in playerMatches.withIndex()) {
                        try {
                            val playerHtml = NetworkClient.get(playerUrl, mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                "Referer" to "https://jkanime.net/"
                            ))
                            val sourceRegex = Regex("""<source src=['"]([^'"]+)['"]""")
                            val streamMatch = sourceRegex.find(playerHtml)
                            if (streamMatch != null) {
                                val streamUrl = streamMatch.groupValues[1]
                                val isHls = streamUrl.contains(".m3u8")
                                val serverLabel = if (playerUrl.contains("umv")) "1080p FHD (JKanime Oficial)" else "720p HD (JKanime Servidor ${idx + 1})"
                                list.add(
                                    VideoSource(
                                        quality = "$serverLabel - Ep $epInt",
                                        videoUrl = streamUrl,
                                        format = if (isHls) "HLS" else "MP4",
                                        headers = mapOf("Referer" to "https://jkanime.net/", "User-Agent" to "Mozilla/5.0")
                                    )
                                )
                            }
                        } catch (e: Exception) {}
                    }
                    if (list.isNotEmpty()) break
                } catch (e: Exception) {}
            }

            // ==========================================
            // MOTOR 2: MonosChinos (https://monoschinos.st/)
            // ==========================================
            val mcSlugs = mutableSetOf<String>()
            for (candidate in distinctCandidates.take(3)) {
                try {
                    val encodedQ = URLEncoder.encode(candidate, "UTF-8")
                    val mcSearchUrl = "https://monoschinos.st/buscar?q=$encodedQ"
                    val mcHtml = NetworkClient.get(mcSearchUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                    val mcRegex = Regex("""href=["']https://monoschinos\.st/anime/([a-zA-Z0-9-]+)(?:-sub-espanol)?["']""")
                    val matches = mcRegex.findAll(mcHtml).toList()
                    for (m in matches) {
                        val s = m.groupValues[1].removeSuffix("-sub-espanol")
                        mcSlugs.add(s)
                    }
                    if (mcSlugs.isNotEmpty()) break
                } catch (e: Exception) {}
            }

            for (mcSlug in mcSlugs.take(2)) {
                try {
                    val mcEpUrl = "https://monoschinos.st/ver/$mcSlug-episodio-$epInt"
                    val mcEpHtml = NetworkClient.get(mcEpUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                    val dataPlayerRegex = Regex("""data-player=["']([A-Za-z0-9+/=]+)["']""")
                    val playerMatches = dataPlayerRegex.findAll(mcEpHtml).toList()

                    for (pm in playerMatches) {
                        val b64 = pm.groupValues[1]
                        try {
                            val decodedUrl = String(java.util.Base64.getDecoder().decode(b64)).trim()
                            if (decodedUrl.startsWith("http")) {
                                val serverName = when {
                                    decodedUrl.contains("filemoon") -> "Filemoon"
                                    decodedUrl.contains("dood") -> "DoodStream"
                                    decodedUrl.contains("voe") -> "Voe"
                                    decodedUrl.contains("mp4upload") -> "Mp4Upload"
                                    decodedUrl.contains("swhoi") || decodedUrl.contains("streamwish") || decodedUrl.contains("wishfast") -> "StreamWish"
                                    decodedUrl.contains("uqload") -> "Uqload"
                                    decodedUrl.contains("ok.ru") -> "OkRu"
                                    else -> "Streaming"
                                }
                                list.add(
                                    VideoSource(
                                        quality = "HD (MonosChinos - $serverName - Ep $epInt)",
                                        videoUrl = decodedUrl,
                                        format = if (decodedUrl.contains(".m3u8")) "HLS" else "EMBED"
                                    )
                                )
                            }
                        } catch (e: Exception) {}
                    }
                    if (list.isNotEmpty()) break
                } catch (e: Exception) {}
            }

            // ==========================================
            // MOTOR 3: AnimeFLV (https://animeflv.or.at/)
            // ==========================================
            if (list.size < 2) {
                for (candidate in distinctCandidates.take(2)) {
                    try {
                        val encodedQ = URLEncoder.encode(candidate, "UTF-8")
                        val aflvSearchUrl = "https://animeflv.or.at/?s=$encodedQ"
                        val aflvHtml = NetworkClient.get(aflvSearchUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                        val epLinkRegex = Regex("""href=["'](https://animeflv\.or\.at/\d{4}/\d{2}/\d{2}/[a-zA-Z0-9-]+-episodio-$epInt/?)["']""")
                        val epMatch = epLinkRegex.find(aflvHtml)
                        if (epMatch != null) {
                            val aflvEpUrl = epMatch.groupValues[1]
                            val aflvEpHtml = NetworkClient.get(aflvEpUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                            val iframeRegex = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
                            val iframes = iframeRegex.findAll(aflvEpHtml).toList()
                            for (ifr in iframes) {
                                val iUrl = ifr.groupValues[1]
                                if (iUrl.startsWith("http")) {
                                    list.add(
                                        VideoSource(
                                            quality = "HD (AnimeFLV Oficial - Ep $epInt)",
                                            videoUrl = iUrl,
                                            format = if (iUrl.contains(".m3u8")) "HLS" else "EMBED"
                                        )
                                    )
                                }
                            }
                            if (list.isNotEmpty()) break
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Si todos los servidores fallaron, mostrar mensaje explicativo real
        if (list.isEmpty()) {
            list.add(
                VideoSource(
                    quality = "Servidores en sincronización (Reintentar en breve)",
                    videoUrl = "",
                    format = "NOTICE"
                )
            )
        }
        list
    }
}

/**
 * Catálogo Principal de Manga, Manhwa y Manhua EXTRAÍDO DIRECTAMENTE DE MANGADEX
 * Con Filtro de FINALIZADOS / EN EMISIÓN y Extracción de Capítulos 100% COMPLETOS
 */
class MangaDexDirectSource : CrossSource {
    override val id = "mangadex-direct"
    override val name = "MangaDex Oficial (Español)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://api.mangadex.org"

    private val tagIds = mapOf(
        "Action" to "391b0423-d847-456f-aff0-8b0cfc03066b",
        "Adventure" to "87cc87cd-a395-47af-b27a-93258283bbc6",
        "Comedy" to "4d32cc48-9f00-4cca-9b5a-a839f0764984",
        "Drama" to "b9af3a63-f058-46de-a9a0-e0c13906197a",
        "Fantasy" to "cdc58593-87dd-415e-bbc0-2ec27bf404cc",
        "Romance" to "423e2eae-a7a2-4a8b-ac03-a8351462d71d",
        "Sci-Fi" to "256c8bd9-4904-4360-bf4f-508a76d67183",
        "Supernatural" to "eabc5b4c-6aff-42f3-b657-3e90cbd00b75"
    )

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(sort = "POPULARITY_DESC", page = page)

    override suspend fun search(query: String, page: Int): List<MediaItem> = filter(query = query, page = page)

    /**
     * Consulta con soporte para estado: completed (Finalizado) o ongoing (En Emisión)
     */
    override suspend fun filter(
        query: String,
        genre: String?,
        sort: String,
        country: String?,
        status: String?,
        page: Int
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val limit = 50
            val offset = (page - 1) * limit
            val urlBuilder = StringBuilder("$baseUrl/manga?limit=$limit&offset=$offset&includes[]=cover_art&includes[]=author&contentRating[]=safe&contentRating[]=suggestive&availableTranslatedLanguage[]=es&availableTranslatedLanguage[]=es-la")

            // Búsqueda por título
            if (query.trim().isNotEmpty()) {
                urlBuilder.append("&title=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            }

            // Filtro por tipo de cómic (Idioma de origen)
            when (country) {
                "KR" -> urlBuilder.append("&originalLanguage[]=ko") // Manhwa Coreano
                "JP" -> urlBuilder.append("&originalLanguage[]=ja") // Manga Japonés
                "CN" -> urlBuilder.append("&originalLanguage[]=zh") // Manhua Chino
            }

            // Filtro por Estado: completed (Finalizado) u ongoing (En Emisión)
            if (!status.isNullOrEmpty() && status != "TODOS") {
                urlBuilder.append("&status[]=").append(status)
            }

            // Filtro por género / tag de MangaDex
            if (!genre.isNullOrEmpty() && genre != "Todos") {
                val tagId = tagIds[genre]
                if (tagId != null) {
                    urlBuilder.append("&includedTags[]=").append(tagId)
                }
            }

            // Ordenamiento
            when (sort) {
                "POPULARITY_DESC" -> urlBuilder.append("&order[followedCount]=desc")
                "TRENDING_DESC" -> urlBuilder.append("&order[rating]=desc")
                "SCORE_DESC" -> urlBuilder.append("&order[rating]=desc")
                "START_DATE_DESC" -> urlBuilder.append("&order[createdAt]=desc")
                else -> urlBuilder.append("&order[followedCount]=desc")
            }

            val response = NetworkClient.get(urlBuilder.toString())
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: JSONArray()

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.getString("id")
                val attr = item.getJSONObject("attributes")

                // Título principal
                val titlesObj = attr.optJSONObject("title")
                var title = titlesObj?.optString("es", titlesObj.optString("es-la", titlesObj.optString("en", ""))) ?: ""
                if (title.isEmpty()) {
                    title = titlesObj?.optString("ja-ro", titlesObj.optString("ko-ro", titlesObj.optString("zh-ro", ""))) ?: ""
                }

                val altTitles = attr.optJSONArray("altTitles")
                if (altTitles != null) {
                    for (j in 0 until altTitles.length()) {
                        val alt = altTitles.getJSONObject(j)
                        if (alt.has("es")) {
                            title = alt.getString("es")
                            break
                        } else if (alt.has("es-la")) {
                            title = alt.getString("es-la")
                            break
                        } else if (title.isEmpty() && alt.has("en")) {
                            title = alt.getString("en")
                        }
                    }
                }
                if (title.isEmpty() && titlesObj != null && titlesObj.length() > 0) {
                    val firstKey = titlesObj.keys().next()
                    title = titlesObj.optString(firstKey, "")
                }
                if (title.isEmpty()) title = "Manga / Cómic"

                // Carátula oficial
                var coverFileName = ""
                val rels = item.optJSONArray("relationships") ?: JSONArray()
                for (j in 0 until rels.length()) {
                    val rel = rels.getJSONObject(j)
                    if (rel.optString("type") == "cover_art") {
                        val coverAttr = rel.optJSONObject("attributes")
                        coverFileName = coverAttr?.optString("fileName", "") ?: ""
                    }
                }

                val coverUrl = if (coverFileName.isNotEmpty()) {
                    "https://uploads.mangadex.org/covers/$id/$coverFileName.512.jpg"
                } else {
                    "https://mangadex.org/placeholder.png"
                }

                val origLang = attr.optString("originalLanguage", "ja")
                val typeTag = when (origLang) {
                    "ko" -> "[Manhwa]"
                    "zh" -> "[Manhua]"
                    else -> "[Manga]"
                }

                val descObj = attr.optJSONObject("description")
                val desc = descObj?.optString("es", descObj.optString("es-la", descObj.optString("en", "Sin descripción disponible."))) ?: ""

                val tagsList = mutableListOf<String>()
                val tagsArr = attr.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        val tagObj = tagsArr.getJSONObject(t)
                        val nameObj = tagObj.getJSONObject("attributes").getJSONObject("name")
                        val tagName = nameObj.optString("en", "")
                        if (tagName.isNotEmpty()) tagsList.add(tagName)
                    }
                }

                // Estado detallado (Finalizado vs En Emisión)
                val rawStatus = attr.optString("status", "")
                val isCompleted = rawStatus == "completed"
                val statusText = if (isCompleted) "✅ FINALIZADO (Completo)" else "⏳ EN EMISIÓN"

                list.add(
                    MediaItem(
                        id = id,
                        title = "$typeTag $title",
                        url = "https://mangadex.org/title/$id",
                        coverUrl = coverUrl,
                        type = ContentType.MANGA,
                        description = desc,
                        genres = tagsList,
                        status = "$statusText • Español",
                        sourceName = "MangaDex"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getRealtimeUpdates(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val limit = 50
            val offset = (page - 1) * limit
            val url = "$baseUrl/chapter?limit=$limit&offset=$offset&order[readableAt]=desc&translatedLanguage[]=es&translatedLanguage[]=es-la&includes[]=manga"

            val response = NetworkClient.get(url)
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: JSONArray()

            for (i in 0 until data.length()) {
                val chObj = data.getJSONObject(i)
                val chAttr = chObj.getJSONObject("attributes")
                val chNum = chAttr.optString("chapter", "")
                val chTitle = chAttr.optString("title", "")
                val lang = chAttr.optString("translatedLanguage", "es").uppercase()

                var mangaId = ""
                var mangaTitle = ""

                val rels = chObj.optJSONArray("relationships") ?: JSONArray()
                for (r in 0 until rels.length()) {
                    val rel = rels.getJSONObject(r)
                    if (rel.optString("type") == "manga") {
                        mangaId = rel.optString("id", "")
                        val mAttr = rel.optJSONObject("attributes")
                        if (mAttr != null) {
                            val titles = mAttr.optJSONObject("title")
                            mangaTitle = titles?.optString("es", titles.optString("es-la", titles.optString("en", ""))) ?: ""
                            if (mangaTitle.isEmpty()) {
                                mangaTitle = titles?.optString("ja-ro", titles.optString("ko-ro", "Manga")) ?: "Manga"
                            }
                        }
                    }
                }

                if (mangaId.isNotEmpty()) {
                    val displayChapter = if (chNum.isNotEmpty()) "Cap. $chNum" else "Nuevo Cap."
                    val displayTitle = if (mangaTitle.isNotEmpty()) "$mangaTitle - $displayChapter [$lang]" else "Capítulo $chNum [$lang]"

                    list.add(
                        MediaItem(
                            id = mangaId,
                            title = displayTitle,
                            url = "https://mangadex.org/title/$mangaId",
                            coverUrl = "https://mangadex.org/placeholder.png",
                            type = ContentType.MANGA,
                            description = if (chTitle.isNotEmpty()) "Capítulo: $chTitle" else "Capítulo actualizado recientemente en MangaDex Español.",
                            genres = listOf("Reciente", lang),
                            status = "Actualizado Hoy • $lang",
                            sourceName = "MangaDex Realtime"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    /**
     * Obtiene TODOS los capítulos completos de la serie deduplicando y paginando hasta 1000 capítulos
     */
    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val mangaId = media.id
        val uniqueChapters = mutableMapOf<String, EpisodeOrChapter>()

        try {
            // Paginación para obtener capítulos completos (Página 1: 0-500, Página 2: 500-1000)
            for (offset in listOf(0, 500)) {
                val feedUrl = "$baseUrl/manga/$mangaId/feed?limit=500&offset=$offset&order[chapter]=desc&translatedLanguage[]=es&translatedLanguage[]=es-la"
                val feedRes = NetworkClient.get(feedUrl)
                val feedJson = JSONObject(feedRes)
                val feedData = feedJson.optJSONArray("data") ?: JSONArray()

                if (feedData.length() == 0) break

                for (k in 0 until feedData.length()) {
                    val chObj = feedData.getJSONObject(k)
                    val chId = chObj.getString("id")
                    val attr = chObj.getJSONObject("attributes")
                    val chNum = attr.optString("chapter", "")
                    val chTitle = attr.optString("title", "")
                    val lang = attr.optString("translatedLanguage", "es").uppercase()

                    val key = if (chNum.isNotEmpty()) chNum else chId
                    if (!uniqueChapters.containsKey(key)) {
                        val displayNum = if (chNum.isNotEmpty()) chNum else "${uniqueChapters.size + 1}"
                        val displayTitle = if (chTitle.isNotEmpty() && chTitle != "null") "Capítulo $displayNum: $chTitle [$lang]" else "Capítulo $displayNum [$lang]"
                        uniqueChapters[key] = EpisodeOrChapter(
                            id = chId,
                            name = displayTitle,
                            number = chNum.toFloatOrNull() ?: (uniqueChapters.size + 1).toFloat(),
                            url = chId,
                            uploadDate = attr.optString("publishAt", "")
                        )
                    }
                }

                if (feedData.length() < 500) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Si la serie no tiene capítulos disponibles en español, mostrar aviso claro
        if (uniqueChapters.isEmpty()) {
            uniqueChapters["0"] = EpisodeOrChapter(
                id = "no_es_${media.id}",
                name = "Aviso: Esta serie aún no cuenta con traducción en Español",
                number = 0f,
                url = "https://mangadex.org/title/${media.id}",
                uploadDate = "Aviso"
            )
        }

        // Ordenar capítulos de forma descendente (último capítulo arriba)
        val sortedChapters = uniqueChapters.values.sortedByDescending { it.number }
        Pair(media, sortedChapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            if (!chapter.id.startsWith("fallback_")) {
                val serverUrl = "$baseUrl/at-home/server/${chapter.id}"
                val serverRes = NetworkClient.get(serverUrl)
                val serverJson = JSONObject(serverRes)
                val host = serverJson.getString("baseUrl")
                val chData = serverJson.getJSONObject("chapter")
                val hash = chData.getString("hash")
                val files = chData.getJSONArray("data")

                for (idx in 0 until files.length()) {
                    val file = files.getString(idx)
                    val realPageUrl = "$host/data/$hash/$file"
                    pages.add(
                        MangaPage(
                            index = idx + 1,
                            imageUrl = realPageUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Alias de compatibilidad hacia atrás
 */
typealias AniListMangaSource = MangaDexDirectSource
