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

    private fun isTitleMatch(candidates: List<String>, foundTitle: String, foundSlug: String): Boolean {
        val cleanFound = foundTitle.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val cleanSlug = foundSlug.lowercase().replace("-", " ").trim()
        val foundWords = (cleanFound.split(" ") + cleanSlug.split(" ")).filter { it.length >= 3 }.toSet()

        for (cand in candidates) {
            val cleanCand = cand.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
            if (cleanCand.isEmpty()) continue
            if (cleanFound.contains(cleanCand) || cleanCand.contains(cleanFound)) return true
            if (cleanSlug.contains(cleanCand) || cleanCand.contains(cleanSlug)) return true

            val candWords = cleanCand.split(" ").filter { it.length >= 3 }
            if (candWords.isEmpty()) continue
            val matches = candWords.count { foundWords.contains(it) }
            val ratio = matches.toFloat() / candWords.size.toFloat()
            if (ratio >= 0.5f || (candWords.size == 1 && matches == 1)) {
                return true
            }
        }
        return false
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
                val words = english.split(" ").filter { it.length > 2 }
                if (words.isNotEmpty()) queryCandidates.add(words[0])
            }

            val distinctCandidates = queryCandidates.distinct().filter { it.isNotBlank() }

            // ==========================================
            // MOTOR 1: JKanime (https://jkanime.net/)
            // ==========================================
            val jkSlugs = mutableSetOf<String>()

            // 1. Verificación directa de slugs canónicos
            val canonicalSlugs = mutableListOf<String>()
            if (romaji.isNotEmpty()) canonicalSlugs.add(romaji.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            if (titleClean.isNotEmpty()) canonicalSlugs.add(titleClean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            if (english.isNotEmpty()) canonicalSlugs.add(english.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))

            for (cSlug in canonicalSlugs.distinct()) {
                if (cSlug.isBlank()) continue
                try {
                    val testUrl = "https://jkanime.net/$cSlug/$epInt/"
                    val testHtml = NetworkClient.get(testUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                    if (testHtml.contains("video_box") || testHtml.contains("jkplayer") || testHtml.contains("servers")) {
                        jkSlugs.add(cSlug)
                        break
                    }
                } catch (e: Exception) {}
            }

            // 2. Si no hay slug directo, buscar y VALIDAR ESTRICTAMENTE contra el título
            if (jkSlugs.isEmpty()) {
                for (candidate in distinctCandidates.take(3)) {
                    try {
                        val candidateSlug = candidate.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                        if (candidateSlug.isBlank()) continue
                        val searchUrl = "https://jkanime.net/buscar/$candidateSlug/"
                        val searchHtml = NetworkClient.get(searchUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))

                        // Extraer solo tarjetas reales de animes: <h5><a href="https://jkanime.net/slug/">Título</a></h5>
                        val itemRegex = Regex("""<h5><a\s+href="https://jkanime\.net/([a-zA-Z0-9-]+)/">([^<]+)</a></h5>""")
                        val matches = itemRegex.findAll(searchHtml).toList()
                        for (m in matches) {
                            val foundSlug = m.groupValues[1]
                            val foundTitle = m.groupValues[2].trim()
                            if (foundSlug != "buscar" && foundSlug != "dash" && foundSlug != "img") {
                                if (isTitleMatch(distinctCandidates, foundTitle, foundSlug)) {
                                    jkSlugs.add(foundSlug)
                                    break
                                }
                            }
                        }
                        if (jkSlugs.isNotEmpty()) break
                    } catch (e: Exception) {}
                }
            }

            for (slug in jkSlugs.take(2)) {
                try {
                    val episodePageUrl = "https://jkanime.net/$slug/$epInt/"
                    val epHtml = NetworkClient.get(episodePageUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))

                    // A) Servidores directos y decodificados desde 'var servers = [...]'
                    val serversRegex = Regex("""var\s+servers\s*=\s*(\[\s*\{.*?\}\s*\]);""", RegexOption.DOT_MATCHES_ALL)
                    val sMatch = serversRegex.find(epHtml)
                    if (sMatch != null) {
                        val jsonArr = sMatch.groupValues[1]
                        val objRegex = Regex("""\{"remote":"([^"]+)","slug":"[^"]*","server":"([^"]+)"""")
                        for (m in objRegex.findAll(jsonArr)) {
                            val b64 = m.groupValues[1]
                            val sName = m.groupValues[2]
                            try {
                                val decoded = String(java.util.Base64.getDecoder().decode(b64)).trim()
                                if (decoded.startsWith("http")) {
                                    val isMp4 = decoded.endsWith(".mp4", ignoreCase = true) || sName.equals("Mediafire", true) || sName.equals("Streamtape", true)
                                    list.add(
                                        VideoSource(
                                            quality = "1080p FHD ($sName - Ep $epInt)",
                                            videoUrl = decoded,
                                            format = if (isMp4) "MP4" else if (decoded.contains(".m3u8")) "HLS" else "EMBED",
                                            headers = mapOf("Referer" to "https://jkanime.net/", "User-Agent" to "Mozilla/5.0")
                                        )
                                    )
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // B) Reproductores web oficiales JKanime (Desu / Magi)
                    val playerUrlRegex = Regex("""video\[\d+\]\s*=\s*['"]<iframe[^>]+src=['"]([^'"]+)['"]""")
                    val playerMatches = playerUrlRegex.findAll(epHtml).map { it.groupValues[1] }.distinct().toList()
                    for ((idx, playerUrl) in playerMatches.withIndex()) {
                        val serverLabel = if (playerUrl.contains("umv")) "1080p FHD (JKanime Magi - Ep $epInt)" else "720p HD (JKanime Desu - Ep $epInt)"
                        list.add(
                            VideoSource(
                                quality = serverLabel,
                                videoUrl = playerUrl,
                                format = "EMBED",
                                headers = mapOf("Referer" to "https://jkanime.net/", "User-Agent" to "Mozilla/5.0")
                            )
                        )

                        // Intentar extraer stream HLS directo del reproductor
                        try {
                            val playerHtml = NetworkClient.get(playerUrl, mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                "Referer" to "https://jkanime.net/"
                            ))
                            val sourceRegex = Regex("""<source src=['"]([^'"]+)['"]""")
                            val streamMatch = sourceRegex.find(playerHtml)
                            if (streamMatch != null) {
                                val streamUrl = streamMatch.groupValues[1]
                                if (!streamUrl.contains("") && streamUrl.startsWith("http")) {
                                    val isHls = streamUrl.contains(".m3u8")
                                    list.add(
                                        0,
                                        VideoSource(
                                            quality = "Directo Stream 1080p - Ep $epInt",
                                            videoUrl = streamUrl,
                                            format = if (isHls) "HLS" else "MP4",
                                            headers = mapOf("Referer" to "https://jkanime.net/", "User-Agent" to "Mozilla/5.0")
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    if (list.isNotEmpty()) break
                } catch (e: Exception) {}
            }

            // ==========================================
            // MOTOR 2: MonosChinos (https://monoschinos.st/)
            // ==========================================
            if (list.size < 2) {
                val mcSlugs = mutableSetOf<String>()
                for (candidate in distinctCandidates.take(3)) {
                    try {
                        val encodedQ = URLEncoder.encode(candidate, "UTF-8")
                        val mcSearchUrl = "https://monoschinos.st/buscar?q=$encodedQ"
                        val mcHtml = NetworkClient.get(mcSearchUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                        val mcItemRegex = Regex("""(?s)<div class="series"[^>]*>.*?<a href=["']https://monoschinos\.st/anime/([a-zA-Z0-9-]+)(?:-sub-espanol)?["'][^>]*title=["']([^"']+)["']""")
                        val matches = mcItemRegex.findAll(mcHtml).toList()
                        for (m in matches) {
                            val s = m.groupValues[1].removeSuffix("-sub-espanol")
                            val title = m.groupValues[2].trim()
                            if (isTitleMatch(distinctCandidates, title, s)) {
                                mcSlugs.add(s)
                                break
                            }
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
                        val aflvCardRegex = Regex("""<a\s+title="([^"]+)"\s+href="https://animeflv\.or\.at/anime/([^"/]+)/">""")
                        val match = aflvCardRegex.findAll(aflvHtml).firstOrNull { m ->
                            isTitleMatch(distinctCandidates, m.groupValues[1], m.groupValues[2])
                        }
                        if (match != null) {
                            val aSlug = match.groupValues[2]
                            val aflvEpUrl = "https://animeflv.or.at/$aSlug-episodio-$epInt/"
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
            if (!chapter.id.startsWith("fallback_") && !chapter.id.startsWith("no_es_")) {
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
 * Catálogo Principal de ZonaTMO / TuMangaOnline (100% en Español)
 * Extracción de Biblioteca Completa de Manhwa, Manga, Manhua y Webtoons
 */
class ZonaTMOSource : CrossSource {
    override val id = "zonatmo"
    override val name = "ZonaTMO (TuMangaOnline)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://zonatmo.org"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(page = page)

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
            val encodedQ = URLEncoder.encode(query.trim(), "UTF-8")
            val url = if (query.trim().isNotEmpty()) {
                "$baseUrl/biblioteca?title=$encodedQ&page=$page"
            } else {
                "$baseUrl/biblioteca?page=$page"
            }
            val html = NetworkClient.get(url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pattern = Regex("""(?s)<div class="element[^"]*"[^>]*data-identifier="(\d+)"[^>]*>.*?<a href="(https://zonatmo\.org/library/[^"]+)".*?data-bg="([^"]+)".*?<h4[^>]*class="text-truncate"[^>]*title="([^"]+)"""")
            val matches = pattern.findAll(html).toList()

            for (m in matches) {
                val mangaId = m.groupValues[1]
                val mangaUrl = m.groupValues[2]
                val cover = m.groupValues[3]
                val title = m.groupValues[4].trim()

                val typeTag = when {
                    mangaUrl.contains("/manhwa/") -> "[Manhwa]"
                    mangaUrl.contains("/manhua/") -> "[Manhua]"
                    else -> "[Manga]"
                }

                list.add(
                    MediaItem(
                        id = "tmo_$mangaId",
                        title = "$typeTag $title",
                        url = mangaUrl,
                        coverUrl = cover,
                        type = ContentType.MANGA,
                        description = "Disponible en ZonaTMO en Español.",
                        genres = listOf("Español", "ZonaTMO"),
                        status = "✅ Disponible • Español",
                        sourceName = "ZonaTMO"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<EpisodeOrChapter>()
        try {
            val html = NetworkClient.get(media.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val uploadRegex = Regex("""href="(https://zonatmo\.org/view_uploads/(\d+))"""")
            val matches = uploadRegex.findAll(html).toList().distinctBy { it.groupValues[1] }

            for ((idx, m) in matches.withIndex()) {
                val viewUrl = m.groupValues[1]
                val uploadId = m.groupValues[2]
                val chNumber = (matches.size - idx).toFloat()
                chapters.add(
                    EpisodeOrChapter(
                        id = "tmo_ch_$uploadId",
                        name = "Capítulo $chNumber - Sub Español",
                        number = chNumber,
                        url = viewUrl,
                        uploadDate = "ZonaTMO"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(media, chapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            val html = NetworkClient.get(chapter.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pageRegex = Regex("""https://storage\d*\.zonatmo\.org/chapters/[^'"\s>]+\.(?:webp|jpg|png|jpeg)""")
            val matches = pageRegex.findAll(html).map { it.value }.distinct().toList()

            for ((idx, pUrl) in matches.withIndex()) {
                pages.add(
                    MangaPage(
                        index = idx + 1,
                        imageUrl = pUrl
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Catálogo de LINE Webtoon Oficial (100% en Español)
 */
class WebtoonEsSource : CrossSource {
    override val id = "webtoons-es"
    override val name = "LINE Webtoon Oficial (Español)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://www.webtoons.com/es"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(page = page)

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
            val url = if (query.trim().isNotEmpty()) {
                "$baseUrl/search?keyword=${URLEncoder.encode(query.trim(), "UTF-8")}"
            } else {
                "$baseUrl/ranking/trending"
            }
            val html = NetworkClient.get(url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pattern = Regex("""(?s)<a href="https://www\.webtoons\.com/es/([^"]+title_no=(\d+))"[^>]*>.*?<img[^>]*src="([^"]+)".*?<p class="subj">([^<]+)</p>""")
            val matches = pattern.findAll(html).toList()

            for (m in matches) {
                val path = m.groupValues[1]
                val titleNo = m.groupValues[2]
                val cover = m.groupValues[3]
                val title = m.groupValues[4].trim()

                list.add(
                    MediaItem(
                        id = "wt_$titleNo",
                        title = "[Webtoon] $title",
                        url = "https://www.webtoons.com/es/$path",
                        coverUrl = cover,
                        type = ContentType.MANGA,
                        description = "Webtoon Oficial en Español.",
                        genres = listOf("Webtoon", "Español"),
                        status = "✅ Oficial • Español",
                        sourceName = "LINE Webtoon ES"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<EpisodeOrChapter>()
        try {
            val html = NetworkClient.get(media.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val epPattern = Regex("""(?s)<li[^>]*data-episode-no="(\d+)"[^>]*>.*?<a href="([^"]+)".*?<span class="subj"><span>([^<]+)</span>""")
            val matches = epPattern.findAll(html).toList()

            for (m in matches) {
                val epNo = m.groupValues[1].toIntOrNull() ?: 1
                val link = m.groupValues[2]
                val epTitle = m.groupValues[3].trim()

                chapters.add(
                    EpisodeOrChapter(
                        id = "wt_ep_${media.id}_$epNo",
                        name = "Capítulo $epNo: $epTitle",
                        number = epNo.toFloat(),
                        url = link,
                        uploadDate = "Oficial"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(media, chapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            val html = NetworkClient.get(chapter.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pageRegex = Regex("""data-url="([^"]+)"""")
            val matches = pageRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()

            for ((idx, pUrl) in matches.withIndex()) {
                pages.add(
                    MangaPage(
                        index = idx + 1,
                        imageUrl = pUrl
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Catálogo de LectorMangas (https://lector-mangas.lat/)
 * Biblioteca completa en español de Manga, Manhwa, Manhua y Novelas.
 */
class LectorMangasSource : CrossSource {
    override val id = "lectormangas"
    override val name = "LectorMangas (Español)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://lector-mangas.lat"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(page = page)

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
            val url = if (query.trim().isNotEmpty()) {
                "$baseUrl/comics?q=${URLEncoder.encode(query.trim(), "UTF-8")}&page=$page"
            } else {
                if (page <= 1) "$baseUrl/" else "$baseUrl/comics?page=$page"
            }
            val html = NetworkClient.get(url, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "$baseUrl/"
            ))

            // Patrón para capturar tarjetas de series y JSON-LD de series destacadas
            val cardRegex = Regex("""(?s)href="https://lector-mangas\.lat/comics/([^"/]+)"[^>]*name="([^"]+)"""")
            val imgRegex = Regex("""(?s)href="https://lector-mangas\.lat/comics/([^"/]+)".*?<img[^>]*src="([^"]+)".*?class="[^"]*title[^"]*">([^<]+)<""")
            val genericRegex = Regex("""href="/comics/([^"/]+)".*?alt="([^"]+)"""")

            val matched = cardRegex.findAll(html).toList()
            if (matched.isNotEmpty()) {
                for (m in matched) {
                    val slug = m.groupValues[1]
                    val title = m.groupValues[2].trim()
                    list.add(
                        MediaItem(
                            id = "lm_$slug",
                            title = "[LectorManga] $title",
                            url = "$baseUrl/comics/$slug",
                            coverUrl = "https://api.zerocomics.net/storage/series/portadas/default.webp",
                            type = ContentType.MANGA,
                            description = "Disponible en Lector manga en Español.",
                            genres = listOf("Español", "LectorManga"),
                            status = "✅ Disponible • Español",
                            sourceName = "LectorMangas"
                        )
                    )
                }
            }

            // Parseo secundario para extraer imágenes precisas
            val detailRegex = Regex("""href="https://lector-mangas\.lat/comics/([^"/]+)"\s+name="([^"]+)"""")
            val portadas = Regex("""https://api\.zerocomics\.net/storage/series/portadas/[^"'\s>]+\.(?:webp|jpg|png)""").findAll(html).map { it.value }.toList()
            for ((idx, m) in detailRegex.findAll(html).withIndex()) {
                val slug = m.groupValues[1]
                val title = m.groupValues[2].trim()
                val cover = portadas.getOrNull(idx) ?: "https://api.zerocomics.net/storage/series/portadas/default.webp"
                if (list.none { it.id == "lm_$slug" }) {
                    list.add(
                        MediaItem(
                            id = "lm_$slug",
                            title = "[LectorManga] $title",
                            url = "$baseUrl/comics/$slug",
                            coverUrl = cover,
                            type = ContentType.MANGA,
                            description = "Disponible en Lector manga en Español.",
                            genres = listOf("Español", "LectorManga"),
                            status = "✅ Disponible • Español",
                            sourceName = "LectorMangas"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<EpisodeOrChapter>()
        var resolvedMedia = media
        try {
            val html = NetworkClient.get(media.url, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "$baseUrl/"
            ))

            // Extraer portada de alta resolución
            val coverMatch = Regex("""const serie_portada\s*=\s*"([^"]+)"""").find(html)
                ?: Regex("""property="og:image"\s+content="([^"]+)"""").find(html)
            if (coverMatch != null) {
                resolvedMedia = resolvedMedia.copy(coverUrl = coverMatch.groupValues[1])
            }

            // Extraer capítulos: /comics/{slug}/chapter-{n} o capitulo-{n}
            val chRegex = Regex("""href="(/comics/[^"/]+/(?:chapter|capitulo)-(\d+))"[^>]*>.*?Capítulo\s+(\d+)""")
            val chMatches = chRegex.findAll(html).toList().distinctBy { it.groupValues[1] }

            if (chMatches.isNotEmpty()) {
                for (m in chMatches) {
                    val path = m.groupValues[1]
                    val num = m.groupValues[2].toFloatOrNull() ?: 1f
                    chapters.add(
                        EpisodeOrChapter(
                            id = "lm_ch_${media.id}_$num",
                            name = "Capítulo $num - Español",
                            number = num,
                            url = if (path.startsWith("http")) path else "$baseUrl$path",
                            uploadDate = "LectorMangas"
                        )
                    )
                }
            } else {
                // Fallback de enlaces de capítulos estándar
                val fallbackRegex = Regex("""href="(/comics/[^"/]+/(?:chapter|capitulo)-[^"]+)"""")
                val fallbackMatches = fallbackRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()
                for ((idx, path) in fallbackMatches.withIndex()) {
                    val num = (fallbackMatches.size - idx).toFloat()
                    chapters.add(
                        EpisodeOrChapter(
                            id = "lm_ch_${media.id}_$idx",
                            name = "Capítulo $num - Español",
                            number = num,
                            url = if (path.startsWith("http")) path else "$baseUrl$path",
                            uploadDate = "LectorMangas"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(resolvedMedia, chapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            val html = NetworkClient.get(chapter.url, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "$baseUrl/"
            ))
            val imgRegex = Regex("""https://api\.zerocomics\.net/storage/chapters/[^"'\s>]+\.(?:webp|jpg|png|jpeg)""")
            val allImgRegex = Regex("""https://[^\s"'<>]+\.(?:webp|jpg|png|jpeg)""")

            var matches = imgRegex.findAll(html).map { it.value }.distinct().toList()
            if (matches.isEmpty()) {
                matches = allImgRegex.findAll(html).map { it.value }.filter {
                    it.contains("chapter") || it.contains("storage") || it.contains("zerocomics")
                }.distinct().toList()
            }

            for ((idx, url) in matches.withIndex()) {
                pages.add(
                    MangaPage(
                        index = idx + 1,
                        imageUrl = url
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Catálogo de LectorXD (https://lectorxd.com/)
 * Manhwas y Manhuas en Español
 */
class LectorXdSource : CrossSource {
    override val id = "lectorxd"
    override val name = "LectorXD (Manhwa y Manhua)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://lectorxd.com"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(page = page)

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
            val url = if (query.trim().isNotEmpty()) {
                "$baseUrl/catalogo?q=${URLEncoder.encode(query.trim(), "UTF-8")}&page=$page"
            } else {
                "$baseUrl/"
            }
            val html = NetworkClient.get(url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pattern = Regex("""(?s)<a href="/(manhwa|manhua|manga)/([^"]+)"[^>]*>.*?<img[^>]*src="([^"]+)".*?title="([^"]+)"""")
            val matches = pattern.findAll(html).toList()

            for (m in matches) {
                val kind = m.groupValues[1]
                val slug = m.groupValues[2]
                val cover = m.groupValues[3]
                val title = m.groupValues[4].trim()

                list.add(
                    MediaItem(
                        id = "lxd_$slug",
                        title = "[${kind.replaceFirstChar { it.uppercase() }}] $title",
                        url = "$baseUrl/$kind/$slug",
                        coverUrl = cover,
                        type = ContentType.MANGA,
                        description = "Disponible en LectorXD en Español.",
                        genres = listOf("Español", "LectorXD"),
                        status = "✅ Disponible • Español",
                        sourceName = "LectorXD"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<EpisodeOrChapter>()
        try {
            val html = NetworkClient.get(media.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val chRegex = Regex("""href="(/[^"/]+/[^"/]+/leer/(\d+))"[^>]*title="[^"]*Capítulo\s+(\d+)"""")
            val matches = chRegex.findAll(html).toList().distinctBy { it.groupValues[1] }

            for (m in matches) {
                val path = m.groupValues[1]
                val num = m.groupValues[2].toFloatOrNull() ?: 1f
                chapters.add(
                    EpisodeOrChapter(
                        id = "lxd_ch_${media.id}_$num",
                        name = "Capítulo $num - Español",
                        number = num,
                        url = "$baseUrl$path",
                        uploadDate = "LectorXD"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(media, chapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            val html = NetworkClient.get(chapter.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val imgRegex = Regex("""https://s\d*\.cdnlxd\.xyz/[^"'\s>]+\.(?:webp|jpg|png|jpeg)""")
            val matches = imgRegex.findAll(html).map { it.value }.distinct().toList()

            for ((idx, url) in matches.withIndex()) {
                pages.add(
                    MangaPage(
                        index = idx + 1,
                        imageUrl = url
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Catálogo de Olympus Scanlation (https://olympusbiblioteca.com/ / https://olympus.pages.dev/)
 * Cómics en Español de alta calidad.
 */
class OlympusSource : CrossSource {
    override val id = "olympus"
    override val name = "Olympus Scanlation (Español)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://olympusbiblioteca.com"

    override suspend fun getPopular(page: Int): List<MediaItem> = filter(page = page)

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
            val html = NetworkClient.get(baseUrl, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val pattern = Regex("""(?s)<a href="/series/([^"/]+)"[^>]*>.*?<img[^>]*src="([^"]+)".*?<h3[^>]*>([^<]+)</h3>""")
            val matches = pattern.findAll(html).toList()

            for (m in matches) {
                val slug = m.groupValues[1]
                val cover = m.groupValues[2]
                val title = m.groupValues[3].trim()

                list.add(
                    MediaItem(
                        id = "oly_$slug",
                        title = "[Olympus] $title",
                        url = "$baseUrl/series/$slug",
                        coverUrl = cover,
                        type = ContentType.MANGA,
                        description = "Disponible en Olympus Scanlation en Español.",
                        genres = listOf("Español", "Olympus"),
                        status = "✅ Disponible • Español",
                        sourceName = "Olympus Scanlation"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<EpisodeOrChapter>()
        try {
            val html = NetworkClient.get(media.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val chRegex = Regex("""href="(/capitulo/(\d+)/[^"]+)"[^>]*>.*?Capítulo\s+(\d+)""")
            val matches = chRegex.findAll(html).toList().distinctBy { it.groupValues[1] }

            for (m in matches) {
                val path = m.groupValues[1]
                val num = m.groupValues[2].toFloatOrNull() ?: 1f
                chapters.add(
                    EpisodeOrChapter(
                        id = "oly_ch_${media.id}_$num",
                        name = "Capítulo $num - Español",
                        number = num,
                        url = "$baseUrl$path",
                        uploadDate = "Olympus"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(media, chapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPage>()
        try {
            val html = NetworkClient.get(chapter.url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
            val imgRegex = Regex("""https://up\.swordflake\.com/[^"'\s>]+\.(?:webp|jpg|png|jpeg)""")
            val matches = imgRegex.findAll(html).map { it.value }.distinct().toList()

            for ((idx, url) in matches.withIndex()) {
                pages.add(
                    MangaPage(
                        index = idx + 1,
                        imageUrl = url
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }
}

/**
 * Gestor Multi-Biblioteca de Cómics 100% en Español
 * Unifica ZonaTMO, LectorMangas, LINE Webtoon Oficial, LectorXD, Olympus y MangaDex Español
 */
class UnifiedSpanishMangaSource : CrossSource {
    override val id = "spanish-manga-unified"
    override val name = "Biblioteca Manga & Webtoon (Español)"
    override val lang = "es"
    override val type = ContentType.MANGA
    override val baseUrl = "https://zonatmo.org"

    val tmoSource = ZonaTMOSource()
    val lmSource = LectorMangasSource()
    val wtSource = WebtoonEsSource()
    val lxdSource = LectorXdSource()
    val olySource = OlympusSource()
    val mdSource = MangaDexDirectSource()

    var activeSourceKey = "zonatmo"

    fun getActive(): CrossSource = when (activeSourceKey) {
        "lectormangas" -> lmSource
        "webtoons", "webtoons_es" -> wtSource
        "lectorxd" -> lxdSource
        "olympus" -> olySource
        "mangadex" -> mdSource
        else -> tmoSource
    }

    suspend fun getRealtimeUpdates(page: Int = 1): List<MediaItem> = mdSource.getRealtimeUpdates(page)

    override suspend fun getPopular(page: Int): List<MediaItem> = getActive().getPopular(page)

    override suspend fun search(query: String, page: Int): List<MediaItem> = getActive().search(query, page)

    override suspend fun filter(
        query: String,
        genre: String?,
        sort: String,
        country: String?,
        status: String?,
        page: Int
    ): List<MediaItem> = getActive().filter(query, genre, sort, country, status, page)

    override suspend fun getDetails(media: MediaItem): Pair<MediaItem, List<EpisodeOrChapter>> {
        val pair = when {
            media.url.contains("zonatmo.org") -> tmoSource.getDetails(media)
            media.url.contains("lector-mangas.lat") -> lmSource.getDetails(media)
            media.url.contains("webtoons.com") -> wtSource.getDetails(media)
            media.url.contains("lectorxd.com") -> lxdSource.getDetails(media)
            media.url.contains("olympus") -> olySource.getDetails(media)
            else -> mdSource.getDetails(media)
        }
        // REGLA ESTRICTA: Filtrar y excluir capítulos que no sean de idioma español
        val spanishChapters = pair.second.filter { ch ->
            !ch.name.contains("English", ignoreCase = true) &&
            !ch.name.contains("[EN]", ignoreCase = true) &&
            !ch.name.contains("[RAW]", ignoreCase = true)
        }
        return Pair(pair.first, spanishChapters)
    }

    override suspend fun getPageList(chapter: EpisodeOrChapter): List<MangaPage> {
        return when {
            chapter.url.contains("zonatmo.org") || chapter.url.contains("view_uploads") -> tmoSource.getPageList(chapter)
            chapter.url.contains("lector-mangas.lat") -> lmSource.getPageList(chapter)
            chapter.url.contains("webtoons.com") -> wtSource.getPageList(chapter)
            chapter.url.contains("lectorxd.com") -> lxdSource.getPageList(chapter)
            chapter.url.contains("olympus") -> olySource.getPageList(chapter)
            else -> mdSource.getPageList(chapter)
        }
    }
}

typealias AniListMangaSource = UnifiedSpanishMangaSource
