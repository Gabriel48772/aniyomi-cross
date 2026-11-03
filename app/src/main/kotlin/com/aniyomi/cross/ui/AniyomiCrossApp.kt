package com.aniyomi.cross.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (playingVideo == null && readingChapter == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.ANIME,
                        onClick = { currentTab = ScreenTab.ANIME; selectedMedia = null },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Anime") },
                        label = { Text("Anime", fontWeight = if (currentTab == ScreenTab.ANIME) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.MANGA,
                        onClick = { currentTab = ScreenTab.MANGA; selectedMedia = null },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Manga") },
                        label = { Text("Manga", fontWeight = if (currentTab == ScreenTab.MANGA) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.secondary
                        )
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
                            accentColor = MaterialTheme.colorScheme.primary,
                            onSelect = { selectedMedia = it }
                        )
                        ScreenTab.MANGA -> MediaCatalogView(
                            title = "Manga & Manhwa",
                            source = mangaSource,
                            accentColor = MaterialTheme.colorScheme.secondary,
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
    accentColor: Color,
    onSelect: (MediaItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var selectedSort by remember { mutableStateOf("POPULARITY_DESC") }
    var selectedCountry by remember { mutableStateOf("TODOS") }

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val genres = listOf("Todos", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Supernatural")
    val isManga = title.contains("Manga", ignoreCase = true)

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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(14.dp))

        // Encabezado con título moderno
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isManga) "Explora los mejores webtoons y mangas" else "Series en emisión y estrenos mundiales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de búsqueda con estilo redondeado
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                applyFilter(1)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por título...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = accentColor) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filtro de País/Tipo (Solo para Manga/Manhwa)
        if (isManga) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCountry == "TODOS",
                    onClick = { selectedCountry = "TODOS"; applyFilter(1) },
                    label = { Text("Todos") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = selectedCountry == "KR",
                    onClick = { selectedCountry = "KR"; applyFilter(1) },
                    label = { Text("🇰🇷 Manhwa") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = selectedCountry == "JP",
                    onClick = { selectedCountry = "JP"; applyFilter(1) },
                    label = { Text("🇯🇵 Manga") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = selectedCountry == "CN",
                    onClick = { selectedCountry = "CN"; applyFilter(1) },
                    label = { Text("🇨🇳 Manhua") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Filtro de Géneros
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(genres) { genre ->
                FilterChip(
                    selected = selectedGenre == genre,
                    onClick = {
                        selectedGenre = genre
                        applyFilter(1)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = Color.White
                    ),
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
                CircularProgressIndicator(color = accentColor)
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron resultados con los filtros actuales.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Banner Hero para el primer elemento
                if (searchQuery.isEmpty() && selectedGenre == "Todos" && items.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        HeroBanner(media = items.first(), accentColor = accentColor, onClick = { onSelect(items.first()) })
                    }
                }

                items(items) { media ->
                    MediaCard(media = media, accentColor = accentColor, onClick = { onSelect(media) })
                }

                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(color = accentColor)
                        } else {
                            Button(
                                onClick = { applyFilter(currentPage + 1) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                modifier = Modifier.fillMaxWidth(0.85f).height(48.dp)
                            ) {
                                Text("Cargar Más Series (+50)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Banner Destacado Hero en la cabecera
@Composable
fun HeroBanner(media: MediaItem, accentColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = media.coverUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
                    .width(220.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor
                ) {
                    Text(
                        text = "DESTACADO HOY",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = media.title.replace(Regex("""\[.*?\]"""), "").trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = media.genres.take(2).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun MediaCard(media: MediaItem, accentColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = media.coverUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Degradado inferior para legibilidad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )
            // Badge superior
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (media.type == ContentType.ANIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            ) {
                Text(
                    text = if (media.type == ContentType.ANIME) "ANIME" else if (media.title.contains("Manhwa")) "MANHWA" else "MANGA",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            // Información en la base
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = media.title.replace(Regex("""\[.*?\]"""), "").trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = media.genres.take(2).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA0A5B9),
                    maxLines = 1
                )
            }
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(details.title.replace(Regex("""\[.*?\]"""), "").trim(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    AsyncImage(
                        model = details.coverUrl,
                        contentDescription = details.title,
                        modifier = Modifier
                            .width(130.dp)
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = details.title.replace(Regex("""\[.*?\]"""), "").trim(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Estado: ${details.status}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            details.genres.take(3).forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = genre,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Sinopsis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.description.ifEmpty { "Sin descripción disponible." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (details.type == ContentType.ANIME) "Episodios Disponibles (${list.size})" else "Capítulos del Cómic (${list.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                items(list) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (details.type == ContentType.ANIME) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = if (details.type == ContentType.ANIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
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
                                    Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color.LightGray)
                                }
                                Button(
                                    onClick = {
                                        if (details.type == ContentType.ANIME) {
                                            onPlayEpisode(item, source)
                                        } else {
                                            onReadChapter(item, source)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (details.type == ContentType.ANIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text(if (details.type == ContentType.ANIME) "Ver" else "Leer", fontWeight = FontWeight.Bold)
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
                .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir", tint = Color.White)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
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
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Keiyoushi, Yuzono y SalmanBappi (${extensions.size} disponibles)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar extensión...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { ext ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (ext.type == ContentType.ANIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (ext.type == ContentType.ANIME) Icons.Default.Movie else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = ext.name,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "v${ext.version} • [${ext.lang.uppercase()}]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { ext.isInstalled = !ext.isInstalled },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (ext.isInstalled) "Listo" else "Obtener")
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
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Episodios y capítulos guardados para disfrutar sin gastar datos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tienes descargas guardadas todavía.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${task.mediaTitle} - ${task.episodeOrChapterName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
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
