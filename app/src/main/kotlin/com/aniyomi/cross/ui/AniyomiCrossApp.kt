package com.aniyomi.cross.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.aniyomi.cross.downloader.CrossDownloadManager
import com.aniyomi.cross.model.*
import com.aniyomi.cross.repository.ExtensionRepositoryManager
import com.aniyomi.cross.scrapers.AniListAnimeSource
import com.aniyomi.cross.scrapers.AniListMangaSource
import com.aniyomi.cross.scrapers.CrossSource
import kotlinx.coroutines.launch

enum class ScreenTab {
    ANIME,
    MANGA,
    EXTENSIONS,
    DOWNLOADS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniyomiCrossApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repoManager = remember { ExtensionRepositoryManager() }
    val downloadManager = remember { CrossDownloadManager(context) }

    val animeSource = remember { AniListAnimeSource() }
    val mangaSource = remember { AniListMangaSource() }

    var currentTab by remember { mutableStateOf(ScreenTab.ANIME) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var playingVideo by remember { mutableStateOf<VideoSource?>(null) }
    var readingChapter by remember { mutableStateOf<EpisodeOrChapter?>(null) }
    var readingPages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }

    Scaffold(
        bottomBar = {
            if (playingVideo == null && readingChapter == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.ANIME,
                        onClick = { currentTab = ScreenTab.ANIME; selectedMedia = null },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Anime") },
                        label = { Text("Anime") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.MANGA,
                        onClick = { currentTab = ScreenTab.MANGA; selectedMedia = null },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Manga") },
                        label = { Text("Manga") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.EXTENSIONS,
                        onClick = { currentTab = ScreenTab.EXTENSIONS; selectedMedia = null },
                        icon = { Icon(Icons.Default.Extension, contentDescription = "Extensiones") },
                        label = { Text("Extensiones") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.DOWNLOADS,
                        onClick = { currentTab = ScreenTab.DOWNLOADS; selectedMedia = null },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Descargas") },
                        label = { Text("Descargas") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                playingVideo != null -> {
                    VideoPlayerView(
                        videoSource = playingVideo!!,
                        onBack = { playingVideo = null }
                    )
                }
                readingChapter != null -> {
                    MangaReaderView(
                        chapter = readingChapter!!,
                        pages = readingPages,
                        onBack = { readingChapter = null }
                    )
                }
                selectedMedia != null -> {
                    MediaDetailView(
                        media = selectedMedia!!,
                        source = if (selectedMedia!!.type == ContentType.ANIME) animeSource else mangaSource,
                        onBack = { selectedMedia = null },
                        onPlayEpisode = { ep, source ->
                            coroutineScope.launch {
                                val videos = source.getVideoList(ep)
                                if (videos.isNotEmpty()) {
                                    playingVideo = videos.first()
                                }
                            }
                        },
                        onReadChapter = { ch, source ->
                            coroutineScope.launch {
                                val pages = source.getPageList(ch)
                                readingPages = pages
                                readingChapter = ch
                            }
                        },
                        onDownloadEpisode = { ep, source ->
                            coroutineScope.launch {
                                val videos = source.getVideoList(ep)
                                if (videos.isNotEmpty()) {
                                    downloadManager.downloadEpisode(selectedMedia!!, ep, videos.first().videoUrl)
                                }
                            }
                        },
                        onDownloadChapter = { ch, source ->
                            coroutineScope.launch {
                                val pages = source.getPageList(ch)
                                if (pages.isNotEmpty()) {
                                    downloadManager.downloadChapter(selectedMedia!!, ch, pages.map { it.imageUrl })
                                }
                            }
                        }
                    )
                }
                else -> {
                    when (currentTab) {
                        ScreenTab.ANIME -> MediaCatalogView(
                            title = "Anime & Series",
                            source = animeSource,
                            onSelect = { selectedMedia = it }
                        )
                        ScreenTab.MANGA -> MediaCatalogView(
                            title = "Manga & Manhwa",
                            source = mangaSource,
                            onSelect = { selectedMedia = it }
                        )
                        ScreenTab.EXTENSIONS -> ExtensionsCatalogView(
                            repoManager = repoManager
                        )
                        ScreenTab.DOWNLOADS -> DownloadsView(
                            downloadManager = downloadManager
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCatalogView(
    title: String,
    source: CrossSource,
    onSelect: (MediaItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var selectedSort by remember { mutableStateOf("POPULARITY_DESC") }
    var selectedCountry by remember { mutableStateOf("TODOS") }

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val genres = listOf("Todos", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Supernatural")
    val isManga = title.contains("Manga", ignoreCase = true)

    var currentPage by remember { mutableStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }

    fun applyFilter(page: Int = 1) {
        coroutineScope.launch {
            if (page == 1) {
                isLoading = true
                currentPage = 1
            } else {
                isLoadingMore = true
            }

            val newItems = source.filter(
                query = searchQuery,
                genre = if (selectedGenre == "Todos") null else selectedGenre,
                sort = selectedSort,
                country = if (selectedCountry == "TODOS") null else selectedCountry,
                page = page
            )

            if (page == 1) {
                items = newItems
                isLoading = false
            } else {
                items = items + newItems
                isLoadingMore = false
                currentPage = page
            }
        }
    }

    LaunchedEffect(Unit) {
        applyFilter(1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                applyFilter()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar anime, manga o manhwa...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filtro de País/Tipo (Solo para Manga/Manhwa)
        if (isManga) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCountry == "TODOS",
                    onClick = { selectedCountry = "TODOS"; applyFilter() },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = selectedCountry == "KR",
                    onClick = { selectedCountry = "KR"; applyFilter() },
                    label = { Text("🇰🇷 Manhwa") }
                )
                FilterChip(
                    selected = selectedCountry == "JP",
                    onClick = { selectedCountry = "JP"; applyFilter() },
                    label = { Text("🇯🇵 Manga") }
                )
                FilterChip(
                    selected = selectedCountry == "CN",
                    onClick = { selectedCountry = "CN"; applyFilter() },
                    label = { Text("🇨🇳 Manhua") }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Filtro de Géneros
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genres) { genre ->
                FilterChip(
                    selected = selectedGenre == genre,
                    onClick = {
                        selectedGenre = genre
                        applyFilter()
                    },
                    label = {
                        val labelEs = when (genre) {
                            "Action" -> "Acción"
                            "Adventure" -> "Aventura"
                            "Comedy" -> "Comedia"
                            "Fantasy" -> "Fantasía"
                            "Supernatural" -> "Sobrenatural"
                            "Sci-Fi" -> "Ciencia Ficción"
                            else -> genre
                        }
                        Text(labelEs)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron resultados con los filtros seleccionados.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { media ->
                    MediaCard(media = media, onClick = { onSelect(media) })
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = { applyFilter(currentPage + 1) },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Text("Cargar Más Series (+50)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCard(media: MediaItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                AsyncImage(
                    model = media.coverUrl,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = if (media.type == ContentType.ANIME) "ANIME" else "MANGA",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailView(
    media: MediaItem,
    source: CrossSource,
    onBack: () -> Unit,
    onPlayEpisode: (EpisodeOrChapter, CrossSource) -> Unit,
    onReadChapter: (EpisodeOrChapter, CrossSource) -> Unit,
    onDownloadEpisode: (EpisodeOrChapter, CrossSource) -> Unit,
    onDownloadChapter: (EpisodeOrChapter, CrossSource) -> Unit
) {
    var details by remember { mutableStateOf(media) }
    var list by remember { mutableStateOf<List<EpisodeOrChapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(media) {
        isLoading = true
        val pair = source.getDetails(media)
        details = pair.first
        list = pair.second
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(details.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    AsyncImage(
                        model = details.coverUrl,
                        contentDescription = details.title,
                        modifier = Modifier
                            .width(120.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fuente: ${details.sourceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Estado: ${details.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            details.genres.take(3).forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sinopsis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.description.ifEmpty { "Sin descripción disponible." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (details.type == ContentType.ANIME) "Episodios (${list.size})" else "Capítulos (${list.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(list) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        if (details.type == ContentType.ANIME) {
                                            onDownloadEpisode(item, source)
                                        } else {
                                            onDownloadChapter(item, source)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Descargar")
                                }
                                Button(
                                    onClick = {
                                        if (details.type == ContentType.ANIME) {
                                            onPlayEpisode(item, source)
                                        } else {
                                            onReadChapter(item, source)
                                        }
                                    }
                                ) {
                                    Text(if (details.type == ContentType.ANIME) "Ver" else "Leer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerView(videoSource: VideoSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = ExoMediaItem.fromUri(videoSource.videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp))
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Salir", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderView(
    chapter: EpisodeOrChapter,
    pages: List<MangaPage>,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text(chapter.name, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pages) { page ->
                AsyncImage(
                    model = page.imageUrl,
                    contentDescription = "Página ${page.index}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ExtensionsCatalogView(repoManager: ExtensionRepositoryManager) {
    var extensions by remember { mutableStateOf<List<ExtensionMeta>>(emptyList()) }
    var filterType by remember { mutableStateOf<ContentType?>(null) }
    var filterLang by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        extensions = repoManager.fetchAllExtensions()
        isLoading = false
    }

    val filtered = remember(extensions, query, filterType, filterLang) {
        repoManager.filterExtensions(extensions, query, filterType, if (filterLang == "all") null else filterLang)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Repositorios & Extensiones",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Keiyoushi, Aniyomi, Yuzono y Salmanbappi (${extensions.size} disponibles)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar extensión (ej. AnimeFLV, TuMangaOnline)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterType == null,
                onClick = { filterType = null },
                label = { Text("Todos") }
            )
            FilterChip(
                selected = filterType == ContentType.ANIME,
                onClick = { filterType = ContentType.ANIME },
                label = { Text("Anime") }
            )
            FilterChip(
                selected = filterType == ContentType.MANGA,
                onClick = { filterType = ContentType.MANGA },
                label = { Text("Manga") }
            )
            FilterChip(
                selected = filterLang == "es",
                onClick = { filterLang = if (filterLang == "es") "all" else "es" },
                label = { Text("Español") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { ext ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ext.iconUrl != null) {
                                AsyncImage(
                                    model = ext.iconUrl,
                                    contentDescription = ext.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (ext.type == ContentType.ANIME) Icons.Default.Movie else Icons.Default.MenuBook,
                                        contentDescription = ext.name
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v${ext.version} • [${ext.lang.uppercase()}] • ${ext.pkg}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    ext.isInstalled = !ext.isInstalled
                                }
                            ) {
                                Text(if (ext.isInstalled) "Instalado" else "Obtener")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsView(downloadManager: CrossDownloadManager) {
    val tasks by downloadManager.tasks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Descargas Offline",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Capítulos y episodios listos para disfrutar sin gastar datos ni conexión",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tienes descargas activas o guardadas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${task.mediaTitle} - ${task.episodeOrChapterName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (task.isCompleted) "Completado (Guardado localmente)" else "Descargando ${(task.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
