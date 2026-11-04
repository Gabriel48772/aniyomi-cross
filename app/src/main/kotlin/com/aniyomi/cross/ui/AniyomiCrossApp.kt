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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.aniyomi.cross.downloader.OfflineStorageManager
import com.aniyomi.cross.downloader.OfflineMedia
import com.aniyomi.cross.updater.AppUpdateManager
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.aniyomi.cross.model.*
import com.aniyomi.cross.repository.ExtensionRepositoryManager
import androidx.compose.foundation.BorderStroke
import com.aniyomi.cross.scrapers.AniListAnimeSource
import com.aniyomi.cross.scrapers.MangaDexDirectSource
import com.aniyomi.cross.scrapers.UnifiedSpanishMangaSource
import com.aniyomi.cross.scrapers.CrossSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val offlineStorage = remember {
        OfflineStorageManager(
            File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AniyomiCrossDownloads")
        )
    }
    val downloadManager = remember { CrossDownloadManager(context, offlineStorage) }

    val animeSource = remember { AniListAnimeSource() }
    val mangaSource = remember { UnifiedSpanishMangaSource() }
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(ScreenTab.ANIME) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var playingVideo by remember { mutableStateOf<VideoSource?>(null) }
    var availableVideoSources by remember { mutableStateOf<List<VideoSource>?>(null) }
    var selectedEpisodeForPlay by remember { mutableStateOf<EpisodeOrChapter?>(null) }
    var readingChapter by remember { mutableStateOf<EpisodeOrChapter?>(null) }
    var readingPages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }

    // Estado del auto-actualizador
    var updateInfo by remember { mutableStateOf<AppUpdateManager.UpdateInfo?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val info = AppUpdateManager.checkLatestRelease(forceManualCheck = false)
        if (info.hasUpdate) {
            updateInfo = info
        }
        // Sondeo continuo de actualizaciones en segundo plano
        while (isActive) {
            delay(60_000)
            val periodic = AppUpdateManager.checkLatestRelease(forceManualCheck = false)
            if (periodic.hasUpdate) {
                updateInfo = periodic
                break
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (playingVideo == null && readingChapter == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ANIYOMI", fontWeight = FontWeight.Black, color = Color.White, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CROSS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = " v${AppUpdateManager.CURRENT_VERSION} ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val info = AppUpdateManager.checkLatestRelease(forceManualCheck = true)
                                    if (info.hasUpdate) {
                                        updateInfo = info
                                    } else {
                                        snackbarHostState.showSnackbar("¡Estás al día! Versión instalada: v${AppUpdateManager.CURRENT_VERSION}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Buscar Actualizaciones",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
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
                                snackbarHostState.showSnackbar("Cargando servidores para ${ep.name}...")
                                val videos = source.getVideoList(ep).filter { it.videoUrl.isNotBlank() && it.format != "NOTICE" }
                                if (videos.isNotEmpty()) {
                                    if (videos.size == 1) {
                                        playingVideo = videos.first()
                                    } else {
                                        selectedEpisodeForPlay = ep
                                        availableVideoSources = videos
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("No se encontraron servidores disponibles para este episodio.")
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
                                val validVideo = videos.firstOrNull { it.videoUrl.isNotBlank() && it.format != "NOTICE" }
                                if (validVideo != null) {
                                    downloadManager.downloadEpisode(selectedMedia!!, ep, validVideo.videoUrl)
                                    snackbarHostState.showSnackbar("Descarga iniciada: ${ep.name}")
                                } else {
                                    snackbarHostState.showSnackbar("No hay servidores disponibles para descargar este episodio.")
                                }
                            }
                        },
                        onDownloadChapter = { ch, source ->
                            coroutineScope.launch {
                                val pages = source.getPageList(ch)
                                if (pages.isNotEmpty()) {
                                    downloadManager.downloadChapter(selectedMedia!!, ch, pages.map { it.imageUrl })
                                    snackbarHostState.showSnackbar("Descarga iniciada: ${ch.name}")
                                } else {
                                    snackbarHostState.showSnackbar("No se pudieron cargar páginas para descargar.")
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
                            downloadManager = downloadManager,
                            offlineStorage = offlineStorage,
                            onReadOfflineManga = { offlineItem ->
                                val pageFiles = offlineItem.localFileOrDir.listFiles()
                                    ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
                                    ?.sortedBy { it.name }
                                    ?: emptyList()
                                val localPages = pageFiles.mapIndexed { idx, f ->
                                    MangaPage(index = idx + 1, imageUrl = "file://${f.absolutePath}")
                                }
                                if (localPages.isNotEmpty()) {
                                    readingPages = localPages
                                    readingChapter = EpisodeOrChapter(
                                        id = offlineItem.id,
                                        name = "${offlineItem.title} - ${offlineItem.subTitle}",
                                        number = 1f,
                                        url = ""
                                    )
                                }
                            },
                            onPlayOfflineAnime = { offlineItem ->
                                playingVideo = VideoSource(
                                    quality = "Offline (Local)",
                                    videoUrl = Uri.fromFile(offlineItem.localFileOrDir).toString(),
                                    format = "MP4"
                                )
                            }
                        )
                    }
                }
            }

            // Selector de Servidor para Anime
            if (availableVideoSources != null && selectedEpisodeForPlay != null) {
                AlertDialog(
                    onDismissRequest = {
                        availableVideoSources = null
                        selectedEpisodeForPlay = null
                    },
                    containerColor = Color(0xFF131524),
                    title = {
                        Column {
                            Text(
                                text = "Selecciona un Servidor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = selectedEpisodeForPlay?.name ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableVideoSources!!) { v ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playingVideo = v
                                            availableVideoSources = null
                                            selectedEpisodeForPlay = null
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2238))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = v.quality,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = if (v.format == "EMBED") "Reproductor Web Rápido" else "Reproductor Nativo HD",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (v.format == "EMBED") Color(0xFF00F0FF) else Color(0xFF10B981)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Ver",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            availableVideoSources = null
                            selectedEpisodeForPlay = null
                        }) {
                            Text("Cancelar", color = Color.LightGray)
                        }
                    }
                )
            }

            // Diálogo de Actualización Automática
            if (updateInfo != null) {
                AlertDialog(
                    onDismissRequest = {
                        if (!isUpdating) {
                            AppUpdateManager.dismissVersion(updateInfo?.latestVersion ?: "")
                            updateInfo = null
                        }
                    },
                    title = {
                        Column {
                            Text("¡Nueva Versión Disponible!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Versión instalada: v${updateInfo?.currentVersion ?: AppUpdateManager.CURRENT_VERSION} ➔ Nueva: v${updateInfo?.latestVersion}",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(updateInfo?.releaseNotes ?: "Mejoras generales y nuevos contenidos.", color = Color.LightGray)
                            if (isUpdating) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LinearProgressIndicator(progress = { updateProgress }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Descargando actualización: ${(updateProgress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    confirmButton = {
                        if (!isUpdating) {
                            Button(
                                onClick = {
                                    val downloadUrl = updateInfo?.apkDownloadUrl ?: "https://github.com/Gabriel48772/aniyomi-cross/raw/main/dist/AniyomiCross-Android.apk"
                                    val newVer = updateInfo?.latestVersion ?: ""
                                    isUpdating = true
                                    coroutineScope.launch {
                                        val apkFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "AniyomiCross-update.apk")
                                        val success = AppUpdateManager.downloadUpdate(downloadUrl, apkFile) { p ->
                                            updateProgress = p
                                        }
                                        isUpdating = false
                                        if (success && apkFile.exists()) {
                                            AppUpdateManager.markVersionInstalled(newVer)
                                            updateInfo = null
                                            try {
                                                val contentUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    apkFile
                                                )
                                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(installIntent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text("Actualizar Ahora")
                            }
                        }
                    },
                    dismissButton = {
                        if (!isUpdating) {
                            TextButton(onClick = {
                                AppUpdateManager.dismissVersion(updateInfo?.latestVersion ?: "")
                                updateInfo = null
                            }) {
                                Text("Más tarde", color = Color.Gray)
                            }
                        }
                    }
                )
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
    var selectedStatus by remember { mutableStateOf("TODOS") }
    var isRealtimeMode by remember { mutableStateOf(false) }
    var isFiltersExpanded by remember { mutableStateOf(false) }

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

            val newItems = if (isManga && isRealtimeMode && source is MangaDexDirectSource) {
                source.getRealtimeUpdates(page)
            } else {
                source.filter(
                    query = searchQuery,
                    genre = if (selectedGenre == "Todos") null else selectedGenre,
                    sort = selectedSort,
                    country = if (selectedCountry == "TODOS") null else selectedCountry,
                    status = if (selectedStatus == "TODOS") null else selectedStatus,
                    page = page
                )
            }

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

        // Barra de búsqueda con botón para desplegar/ocultar filtros
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    applyFilter(1)
                },
                modifier = Modifier.weight(1f),
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

            // Botón interactivo para ocultar / mostrar menú de filtros
            IconButton(
                onClick = { isFiltersExpanded = !isFiltersExpanded },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (isFiltersExpanded) accentColor else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtros",
                    tint = if (isFiltersExpanded) Color.White else accentColor
                )
            }
        }

        // Panel de Filtros Desplegable y Ocultable (No bloquea el catálogo)
        AnimatedVisibility(
            visible = isFiltersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 1. Selector Unificado de Estado (Finalizados vs En Emisión)
                    Text("Estado de la Serie:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("TODOS" to "Todos", "completed" to "✅ Finalizados", "ongoing" to "⏳ En Emisión").forEach { (sKey, sLabel) ->
                            FilterChip(
                                selected = selectedStatus == sKey,
                                onClick = { selectedStatus = sKey; applyFilter(1) },
                                label = { Text(sLabel, fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // 2. Si es Manga: Bibliotecas en Español, Tipo de Cómic y Modo
                    if (isManga) {
                        var currentSourceKey by remember { mutableStateOf("zonatmo") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Biblioteca en Español:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sourcesList = listOf(
                                "zonatmo" to "📚 ZonaTMO",
                                "lectormangas" to "📖 LectorMangas",
                                "webtoons_es" to "📱 LINE Webtoon",
                                "lectorxd" to "⚡ LectorXD",
                                "olympus" to "🏛️ Olympus",
                                "mangadex" to "🌐 MangaDex"
                            )
                            items(sourcesList) { (sKey, sLabel) ->
                                FilterChip(
                                    selected = currentSourceKey == sKey,
                                    onClick = {
                                        currentSourceKey = sKey
                                        (source as? UnifiedSpanishMangaSource)?.activeSourceKey = sKey
                                        applyFilter(1)
                                    },
                                    label = { Text(sLabel, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tipo de Cómic:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("TODOS" to "Todos", "KR" to "🇰🇷 Manhwa", "JP" to "🇯🇵 Manga", "CN" to "🇨🇳 Manhua").forEach { (cKey, cLabel) ->
                                FilterChip(
                                    selected = selectedCountry == cKey,
                                    onClick = { selectedCountry = cKey; applyFilter(1) },
                                    label = { Text(cLabel, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = !isRealtimeMode,
                                onClick = { isRealtimeMode = false; applyFilter(1) },
                                label = { Text("🔥 Populares", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterChip(
                                selected = isRealtimeMode,
                                onClick = { isRealtimeMode = true; applyFilter(1) },
                                label = { Text("⚡ Tiempo Real", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // 3. Filtro de Géneros
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Género:", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(genres) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = {
                                    selectedGenre = genre
                                    applyFilter(1)
                                },
                                shape = RoundedCornerShape(10.dp),
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
                                    Text(labelEs, fontSize = 11.sp)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isFiltersExpanded = false }) {
                            Text("Ocultar Filtros ▲", color = accentColor, fontSize = 12.sp)
                        }
                    }
                }
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
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<EpisodeOrChapter>() }
    val coroutineScope = rememberCoroutineScope()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (details.type == ContentType.ANIME) "Episodios Disponibles (${list.size})" else "Capítulos del Cómic (${list.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    var showDownloadDropdown by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { showDownloadDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Descarga Masiva", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descargar ▾", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showDownloadDropdown,
                            onDismissRequest = { showDownloadDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("⬇ Descargar capítulo seleccionado") },
                                onClick = {
                                    showDownloadDropdown = false
                                    list.firstOrNull()?.let { first ->
                                        if (details.type == ContentType.ANIME) onDownloadEpisode(first, source)
                                        else onDownloadChapter(first, source)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📦 Descargar TODOS (${list.size} en lote)") },
                                onClick = {
                                    showDownloadDropdown = false
                                    coroutineScope.launch {
                                        for (item in list) {
                                            if (details.type == ContentType.ANIME) onDownloadEpisode(item, source)
                                            else onDownloadChapter(item, source)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("☑ Seleccionar capítulos...") },
                                onClick = {
                                    showDownloadDropdown = false
                                    isMultiSelectMode = true
                                }
                            )
                        }
                    }
                }
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
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = selectedItems.contains(item),
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) selectedItems.add(item)
                                            else selectedItems.remove(item)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = if (details.type == ContentType.ANIME) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = if (details.type == ContentType.ANIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                var showItemDropdown by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showItemDropdown = true }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color.LightGray)
                                    }

                                    DropdownMenu(
                                        expanded = showItemDropdown,
                                        onDismissRequest = { showItemDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("⬇ Descargar este capítulo") },
                                            onClick = {
                                                showItemDropdown = false
                                                if (details.type == ContentType.ANIME) onDownloadEpisode(item, source)
                                                else onDownloadChapter(item, source)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📦 Descargar TODOS (${list.size})") },
                                            onClick = {
                                                showItemDropdown = false
                                                coroutineScope.launch {
                                                    for (ch in list) {
                                                        if (details.type == ContentType.ANIME) onDownloadEpisode(ch, source)
                                                        else onDownloadChapter(ch, source)
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("☑ Seleccionar capítulos...") },
                                            onClick = {
                                                showItemDropdown = false
                                                isMultiSelectMode = true
                                            }
                                        )
                                    }
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

        if (isMultiSelectMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedItems.size} seleccionados", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { isMultiSelectMode = false; selectedItems.clear() }) {
                            Text("Cancelar", color = Color.LightGray)
                        }
                        Button(
                            onClick = {
                                val toDownload = selectedItems.toList()
                                isMultiSelectMode = false
                                selectedItems.clear()
                                coroutineScope.launch {
                                    for (ch in toDownload) {
                                        if (details.type == ContentType.ANIME) onDownloadEpisode(ch, source)
                                        else onDownloadChapter(ch, source)
                                    }
                                }
                            },
                            enabled = selectedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Descargar (${selectedItems.size})")
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
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isEmbed = videoSource.format == "EMBED"

    val exoPlayer = remember(videoSource.videoUrl) {
        if (isEmbed) null
        else {
            ExoPlayer.Builder(context).build().apply {
                val uri = Uri.parse(videoSource.videoUrl)
                val mediaItem = ExoMediaItem.fromUri(uri)

                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(20000)

                if (videoSource.headers.isNotEmpty()) {
                    httpDataSourceFactory.setDefaultRequestProperties(videoSource.headers)
                }

                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

                val mediaSource = if (videoSource.videoUrl.contains(".m3u8") || videoSource.format == "HLS") {
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }

                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true

                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == androidx.media3.common.Player.STATE_BUFFERING)
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isBuffering = false
                        errorMessage = "El reproductor nativo no pudo reproducir este enlace directo. Toca 'Abrir en Navegador' para verlo sin interrupciones."
                    }
                })
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isEmbed) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = android.webkit.WebChromeClient()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isBuffering = false
                            }
                        }

                        val reqHeaders = mutableMapOf<String, String>()
                        reqHeaders["Referer"] = videoSource.headers["Referer"] ?: "https://jkanime.net/"
                        loadUrl(videoSource.videoUrl, reqHeaders)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            exoPlayer?.let { player ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isEmbed && isBuffering && errorMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cargando video en HD...", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // Barra superior con botón volver, calidad y opción de abrir en navegador
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir", tint = Color.White)
            }

            Text(
                text = videoSource.quality,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )

            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoSource.videoUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Navegador 🌐", fontSize = 12.sp)
            }
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMessage ?: "", color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoSource.videoUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Abrir en Navegador (Chrome)")
                        }
                        Button(
                            onClick = {
                                errorMessage = null
                                isBuffering = true
                                exoPlayer?.prepare()
                                exoPlayer?.play()
                            }
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}

enum class MangaReadingMode {
    WEBTOON,
    PAGED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderView(
    chapter: EpisodeOrChapter,
    pages: List<MangaPage>,
    onBack: () -> Unit
) {
    var readingMode by remember { mutableStateOf(MangaReadingMode.WEBTOON) }
    var showControls by remember { mutableStateOf(true) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        if (readingMode == MangaReadingMode.WEBTOON && pages.isNotEmpty()) {
            currentPageIndex = lazyListState.firstVisibleItemIndex.coerceIn(0, pages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .clickable { showControls = !showControls }
    ) {
        if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            when (readingMode) {
                MangaReadingMode.WEBTOON -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pages) { page ->
                            AsyncImage(
                                model = page.imageUrl,
                                contentDescription = "Página ${page.index}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }
                    }
                }
                MangaReadingMode.PAGED -> {
                    val page = pages.getOrNull(currentPageIndex)
                    if (page != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = page.imageUrl,
                                contentDescription = "Página ${page.index}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Overlay Superior estilo Aniyomi
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(chapter.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "MangaDex Oficial • Página ${currentPageIndex + 1} de ${pages.size}",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }

        // Overlay Inferior estilo Aniyomi con Slider y Selector de Modo
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (pages.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${currentPageIndex + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = (currentPageIndex + 1).toFloat(),
                                onValueChange = { newVal ->
                                    val targetIdx = (newVal.toInt() - 1).coerceIn(0, pages.size - 1)
                                    currentPageIndex = targetIdx
                                    if (readingMode == MangaReadingMode.WEBTOON) {
                                        coroutineScope.launch {
                                            lazyListState.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                valueRange = 1f..pages.size.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text("${pages.size}", color = Color.Gray, fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                readingMode = if (readingMode == MangaReadingMode.WEBTOON) {
                                    MangaReadingMode.PAGED
                                } else {
                                    MangaReadingMode.WEBTOON
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2030)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (readingMode == MangaReadingMode.WEBTOON) "📜 Cascada Webtoon" else "📖 Modo Paginado",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }

                        if (readingMode == MangaReadingMode.PAGED) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                    enabled = currentPageIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = if (currentPageIndex > 0) Color.White else Color.DarkGray)
                                }
                                IconButton(
                                    onClick = { if (currentPageIndex < pages.size - 1) currentPageIndex++ },
                                    enabled = currentPageIndex < pages.size - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente", tint = if (currentPageIndex < pages.size - 1) Color.White else Color.DarkGray)
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
fun DownloadsView(
    downloadManager: CrossDownloadManager,
    offlineStorage: OfflineStorageManager,
    onReadOfflineManga: (OfflineMedia) -> Unit,
    onPlayOfflineAnime: (OfflineMedia) -> Unit
) {
    val tasks by downloadManager.tasks.collectAsState()
    var selectedSection by remember { mutableStateOf("MANGAS") }
    var refreshKey by remember { mutableIntStateOf(0) }

    val downloadedMangas = remember(refreshKey) { offlineStorage.getDownloadedMangas() }
    val downloadedAnimes = remember(refreshKey) { offlineStorage.getDownloadedAnimes() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Gestor de Descargas Offline",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Disfruta tus mangas y animes guardados 100% sin conexión a internet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Selector de Categoría Offline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSection == "MANGAS",
                onClick = { selectedSection = "MANGAS" },
                label = { Text("📖 Mangas (${downloadedMangas.size})") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedSection == "ANIMES",
                onClick = { selectedSection = "ANIMES" },
                label = { Text("🎬 Animes (${downloadedAnimes.size})") },
                shape = RoundedCornerShape(12.dp)
            )
            if (tasks.isNotEmpty()) {
                FilterChip(
                    selected = selectedSection == "ACTIVAS",
                    onClick = { selectedSection = "ACTIVAS" },
                    label = { Text("⏳ En Curso (${tasks.size})") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedSection) {
            "MANGAS" -> {
                if (downloadedMangas.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No tienes capítulos de manga descargados.\nToca 'Descargar Capítulo' para leerlos sin internet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(downloadedMangas) { item ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${item.subTitle} • ${item.pageCountOrSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Button(
                                        onClick = { onReadOfflineManga(item) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Leer Offline")
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            offlineStorage.deleteOfflineItem(item)
                                            refreshKey++
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "ANIMES" -> {
                if (downloadedAnimes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No tienes episodios de anime descargados.\nToca 'Descargar Episodio' para verlos offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(downloadedAnimes) { item ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${item.subTitle} • ${item.pageCountOrSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Button(
                                        onClick = { onPlayOfflineAnime(item) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Ver Offline")
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            offlineStorage.deleteOfflineItem(item)
                                            refreshKey++
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "ACTIVAS" -> {
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
                                    text = if (task.isCompleted) "Completado y guardado en almacenamiento" else "Descargando ${(task.progress * 100).toInt()}%",
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
}
