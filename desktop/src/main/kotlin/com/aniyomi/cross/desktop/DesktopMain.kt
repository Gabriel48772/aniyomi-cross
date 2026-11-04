package com.aniyomi.cross.desktop

import com.aniyomi.cross.model.ContentType
import com.aniyomi.cross.model.EpisodeOrChapter
import com.aniyomi.cross.model.MangaPage
import com.aniyomi.cross.model.MediaItem
import com.aniyomi.cross.model.VideoSource
import com.aniyomi.cross.repository.ExtensionRepositoryManager
import com.aniyomi.cross.scrapers.AniListAnimeSource
import com.aniyomi.cross.scrapers.MangaDexDirectSource
import com.aniyomi.cross.scrapers.UnifiedSpanishMangaSource
import com.aniyomi.cross.downloader.OfflineStorageManager
import com.aniyomi.cross.downloader.OfflineMedia
import com.aniyomi.cross.updater.AppUpdateManager
import okhttp3.Request
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.border.EmptyBorder

// ==========================================
// PALETA DE COLORES ULTRA-MODERNA (STREAMING DARK)
// ==========================================
object Theme {
    val BG_MAIN = Color(12, 13, 20)           // Fondo principal obsidiana
    val BG_SURFACE = Color(20, 22, 34)        // Fondo de tarjetas y barras
    val BG_SURFACE_HOVER = Color(32, 35, 54)  // Hover de tarjetas
    val BG_INPUT = Color(28, 30, 46)          // Fondo inputs y combos
    val ACCENT_PURPLE = Color(139, 92, 246)   // Púrpura eléctrico neón
    val ACCENT_ROSE = Color(244, 63, 94)      // Rosa / Coral neón
    val ACCENT_CYAN = Color(6, 182, 212)      // Cian / Turquesa
    val BORDER_COLOR = Color(42, 45, 68)      // Bordes sutiles modernos
    val BORDER_ACTIVE = Color(139, 92, 246)
    val TEXT_WHITE = Color(248, 249, 250)
    val TEXT_MUTED = Color(160, 165, 185)
    val BADGE_BG = Color(0, 0, 0, 180)
    val GOLD_STAR = Color(251, 191, 36)
}

fun main() {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")

    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
        } catch (e: Exception) {
            // Default
        }
        val app = AniyomiDesktopApp()
        app.isVisible = true
    }
}

class AniyomiDesktopApp : JFrame("Aniyomi Cross • Cinema & Manga Edition") {

    private val scope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private val repoManager = ExtensionRepositoryManager()
    private val animeSource = AniListAnimeSource()
    private val mangaSource = UnifiedSpanishMangaSource()
    private val offlineStorage = OfflineStorageManager(File(System.getProperty("user.home"), "AniyomiCrossDownloads"))
    private val offlineMediaList = mutableListOf<OfflineMedia>()

    // Contenedores principales
    private val mainContentPanel = JPanel(CardLayout())
    private val cardLayout = mainContentPanel.layout as CardLayout

    private val animePanel = JPanel(BorderLayout())
    private val mangaPanel = JPanel(BorderLayout())
    private val extensionsPanel = JPanel(BorderLayout())
    private val downloadsPanel = JPanel(BorderLayout())

    // Grillas de tarjetas
    private val animeGridPanel = JPanel(GridLayout(0, 5, 16, 20))
    private val mangaGridPanel = JPanel(GridLayout(0, 5, 16, 20))
    private val extensionsListModel = DefaultListModel<String>()
    private val downloadsListModel = DefaultListModel<String>()

    private var currentTab = "ANIME"
    private var animePage = 1
    private var mangaPage = 1

    private val genresList = arrayOf(
        "Todos", "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Horror", "Mystery", "Psychological", "Romance", "Sci-Fi", "Sports", "Supernatural"
    )

    private val genreDisplayMap = mapOf(
        "Todos" to "Todos los Géneros",
        "Action" to "Acción",
        "Adventure" to "Aventura",
        "Comedy" to "Comedia",
        "Drama" to "Drama",
        "Fantasy" to "Fantasía",
        "Horror" to "Terror",
        "Mystery" to "Misterio",
        "Psychological" to "Psicológico",
        "Romance" to "Romance",
        "Sci-Fi" to "Ciencia Ficción",
        "Sports" to "Deportes",
        "Supernatural" to "Sobrenatural"
    )

    private val sortOptions = arrayOf(
        "Más Populares" to "POPULARITY_DESC",
        "Tendencias / En Emisión" to "TRENDING_DESC",
        "Mejor Puntuados" to "SCORE_DESC",
        "Más Recientes" to "START_DATE_DESC"
    )

    // Botones de la barra de navegación superior
    private lateinit var tabAnimeBtn: JButton
    private lateinit var tabMangaBtn: JButton
    private lateinit var tabExtBtn: JButton
    private lateinit var tabDownBtn: JButton

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1320, 860)
        minimumSize = Dimension(1024, 720)
        setLocationRelativeTo(null)
        contentPane.background = Theme.BG_MAIN

        try {
            val iconStream = javaClass.getResourceAsStream("/icons/icon.png")
            if (iconStream != null) {
                val iconImg = ImageIO.read(iconStream)
                this.iconImage = iconImg
                if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    Taskbar.getTaskbar().iconImage = iconImg
                }
            }
        } catch (e: Exception) {}

        setupHeader()
        setupContentPanels()
        loadInitialData()
        refreshOfflineDownloads()
        checkForUpdates(forceManualCheck = false)

        // Verificador continuo de actualizaciones en segundo plano cada minuto
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000)
                val update = AppUpdateManager.checkLatestRelease(forceManualCheck = false)
                if (update.hasUpdate) {
                    withContext(Dispatchers.Swing) {
                        showDesktopUpdateDialog(update)
                    }
                    break
                }
            }
        }
    }

    private fun setupHeader() {
        val header = JPanel(BorderLayout())
        header.background = Theme.BG_SURFACE
        header.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(12, 24, 12, 24)
        )

        // Logo estilizado con degradado visual
        val logoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0))
        logoPanel.isOpaque = false

        val logoBadge = JLabel("ANIYOMI")
        logoBadge.font = Font("Segoe UI", Font.BOLD, 18)
        logoBadge.foreground = Color.WHITE
        val logoSub = JLabel("CROSS")
        logoSub.font = Font("Segoe UI", Font.BOLD, 18)
        logoSub.foreground = Theme.ACCENT_PURPLE

        val tagPill = JLabel(" v${AppUpdateManager.CURRENT_VERSION} ")
        tagPill.font = Font("Segoe UI", Font.BOLD, 11)
        tagPill.foreground = Theme.ACCENT_CYAN
        tagPill.border = RoundedBorder(Theme.ACCENT_CYAN, 8, 1)

        logoPanel.add(logoBadge)
        logoPanel.add(logoSub)
        logoPanel.add(tagPill)

        // Botones de Navegación Estilo Pill
        val navTabs = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        navTabs.isOpaque = false

        tabAnimeBtn = createNavButton("📺 Anime & Series", true)
        tabMangaBtn = createNavButton("📖 Manga & Manhwa", false)
        tabExtBtn = createNavButton("🧩 Extensiones", false)
        tabDownBtn = createNavButton("💾 Descargas", false)

        val checkUpdateBtn = createActionButton("🔄 Actualizaciones", Theme.BG_INPUT)
        checkUpdateBtn.toolTipText = "Verificar si hay nuevas actualizaciones en GitHub"
        checkUpdateBtn.font = Font("Segoe UI", Font.BOLD, 12)
        checkUpdateBtn.addActionListener {
            checkForUpdates(forceManualCheck = true)
        }

        tabAnimeBtn.addActionListener { selectTab("ANIME", tabAnimeBtn) }
        tabMangaBtn.addActionListener { selectTab("MANGA", tabMangaBtn) }
        tabExtBtn.addActionListener { selectTab("EXTENSIONS", tabExtBtn) }
        tabDownBtn.addActionListener { selectTab("DOWNLOADS", tabDownBtn) }

        navTabs.add(tabAnimeBtn)
        navTabs.add(tabMangaBtn)
        navTabs.add(tabExtBtn)
        navTabs.add(tabDownBtn)
        navTabs.add(checkUpdateBtn)

        header.add(logoPanel, BorderLayout.WEST)
        header.add(navTabs, BorderLayout.EAST)

        contentPane.add(header, BorderLayout.NORTH)
    }

    private fun selectTab(tabName: String, activeBtn: JButton) {
        currentTab = tabName
        cardLayout.show(mainContentPanel, tabName)

        val buttons = listOf(tabAnimeBtn, tabMangaBtn, tabExtBtn, tabDownBtn)
        buttons.forEach { btn ->
            val isActive = (btn == activeBtn)
            btn.background = if (isActive) Theme.ACCENT_PURPLE else Theme.BG_SURFACE
            btn.foreground = if (isActive) Color.WHITE else Theme.TEXT_MUTED
            btn.border = RoundedBorder(if (isActive) Theme.ACCENT_PURPLE else Theme.BORDER_COLOR, 14, 1)
        }
    }

    private fun createNavButton(title: String, active: Boolean): JButton {
        val btn = JButton(title)
        btn.font = Font("Segoe UI", Font.BOLD, 13)
        btn.isFocusPainted = false
        btn.isContentAreaFilled = false
        btn.isOpaque = true
        btn.background = if (active) Theme.ACCENT_PURPLE else Theme.BG_SURFACE
        btn.foreground = if (active) Color.WHITE else Theme.TEXT_MUTED
        btn.border = RoundedBorder(if (active) Theme.ACCENT_PURPLE else Theme.BORDER_COLOR, 14, 1)
        btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        btn.margin = Insets(6, 16, 6, 16)
        return btn
    }

    private fun setupContentPanels() {
        mainContentPanel.isOpaque = false
        setupAnimeTab()
        setupMangaTab()
        setupExtensionsTab()
        setupDownloadsTab()

        mainContentPanel.add(animePanel, "ANIME")
        mainContentPanel.add(mangaPanel, "MANGA")
        mainContentPanel.add(extensionsPanel, "EXTENSIONS")
        mainContentPanel.add(downloadsPanel, "DOWNLOADS")

        contentPane.add(mainContentPanel, BorderLayout.CENTER)
    }

    private fun setupAnimeTab() {
        animePanel.isOpaque = false

        val filterContainer = JPanel(BorderLayout())
        filterContainer.background = Theme.BG_SURFACE
        filterContainer.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(8, 20, 8, 20)
        )

        val topSearchRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 4))
        topSearchRow.isOpaque = false

        val searchField = createStyledTextField("Buscar anime por título...")
        val searchBtn = createActionButton("🔍 Buscar", Theme.ACCENT_PURPLE)
        val toggleFiltersBtn = createActionButton("⚙ Filtros Avanzados ▼", Theme.BG_INPUT)

        topSearchRow.add(JLabel("Anime:").apply {
            foreground = Theme.TEXT_MUTED
            font = Font("Segoe UI", Font.BOLD, 13)
        })
        topSearchRow.add(searchField)
        topSearchRow.add(searchBtn)
        topSearchRow.add(toggleFiltersBtn)

        // Panel de filtros avanzados (Ocultable por defecto para no tapar el catálogo)
        val advancedFiltersPanel = JPanel(FlowLayout(FlowLayout.LEFT, 14, 6))
        advancedFiltersPanel.isOpaque = false
        advancedFiltersPanel.isVisible = false

        val statusOptions = arrayOf(
            "Todos los Estados" to "TODOS",
            "✅ Solo Finalizados / Completos" to "completed",
            "⏳ Solo En Emisión" to "ongoing"
        )
        val statusCombo = createStyledComboBox(statusOptions.map { it.first }.toTypedArray())
        val genreCombo = createStyledComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        val sortCombo = createStyledComboBox(sortOptions.map { it.first }.toTypedArray())

        toggleFiltersBtn.addActionListener {
            advancedFiltersPanel.isVisible = !advancedFiltersPanel.isVisible
            toggleFiltersBtn.text = if (advancedFiltersPanel.isVisible) "⚙ Ocultar Filtros ▲" else "⚙ Filtros Avanzados ▼"
            filterContainer.revalidate()
            filterContainer.repaint()
        }

        advancedFiltersPanel.add(JLabel("Estado:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(statusCombo)
        advancedFiltersPanel.add(JLabel("Género:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(genreCombo)
        advancedFiltersPanel.add(JLabel("Orden:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(sortCombo)

        filterContainer.add(topSearchRow, BorderLayout.NORTH)
        filterContainer.add(advancedFiltersPanel, BorderLayout.SOUTH)

        // Paginación
        val bottomPagination = JPanel(FlowLayout(FlowLayout.CENTER, 20, 14))
        bottomPagination.background = Theme.BG_SURFACE
        bottomPagination.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val prevBtn = createActionButton("◀ Página Anterior", Theme.BG_INPUT)
        val pageLabel = JLabel("Página 1")
        pageLabel.font = Font("Segoe UI", Font.BOLD, 14)
        pageLabel.foreground = Theme.TEXT_WHITE
        val nextBtn = createActionButton("Página Siguiente (+50 Series) ▶", Theme.ACCENT_PURPLE)

        fun loadAnimePage(p: Int) {
            animePage = p.coerceAtLeast(1)
            pageLabel.text = "Página $animePage"
            prevBtn.isEnabled = animePage > 1

            val q = searchField.text.trim().let { if (it == "Buscar anime por título...") "" else it }
            val selectedGenre = genresList[genreCombo.selectedIndex]
            val selectedSort = sortOptions[sortCombo.selectedIndex].second
            val selectedStatus = statusOptions[statusCombo.selectedIndex].second

            scope.launch {
                animeGridPanel.removeAll()
                animeGridPanel.add(createLoadingNotice("Cargando animes (Página $animePage)..."))
                animeGridPanel.revalidate()
                animeGridPanel.repaint()

                val results = animeSource.filter(
                    query = q,
                    genre = if (selectedGenre == "Todos") null else selectedGenre,
                    sort = selectedSort,
                    status = if (selectedStatus == "TODOS") null else selectedStatus,
                    page = animePage
                )
                renderMediaGrid(animeGridPanel, results, isAnime = true)
            }
        }

        searchBtn.addActionListener { loadAnimePage(1) }
        searchField.addActionListener { loadAnimePage(1) }
        statusCombo.addActionListener { loadAnimePage(1) }
        genreCombo.addActionListener { loadAnimePage(1) }
        sortCombo.addActionListener { loadAnimePage(1) }
        prevBtn.addActionListener { if (animePage > 1) loadAnimePage(animePage - 1) }
        nextBtn.addActionListener { loadAnimePage(animePage + 1) }

        bottomPagination.add(prevBtn)
        bottomPagination.add(pageLabel)
        bottomPagination.add(nextBtn)

        animeGridPanel.isOpaque = false
        val scroll = JScrollPane(animeGridPanel)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.border = EmptyBorder(16, 20, 16, 20)
        scroll.verticalScrollBar.unitIncrement = 25
        styleScrollBar(scroll)

        animePanel.add(filterContainer, BorderLayout.NORTH)
        animePanel.add(scroll, BorderLayout.CENTER)
        animePanel.add(bottomPagination, BorderLayout.SOUTH)
    }

    private fun setupMangaTab() {
        mangaPanel.isOpaque = false

        val filterContainer = JPanel(BorderLayout())
        filterContainer.background = Theme.BG_SURFACE
        filterContainer.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(8, 20, 8, 20)
        )

        val topSearchRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 4))
        topSearchRow.isOpaque = false

        val sourceOptions = arrayOf(
            "📚 ZonaTMO (TuMangaOnline)",
            "📖 LectorMangas (Latinoamérica)",
            "📱 LINE Webtoon (Oficial Español)",
            "⚡ LectorXD (Manhwa y Manhua)",
            "🏛️ Olympus Scanlation (Español)",
            "🌐 MangaDex (Comunidad Español)"
        )
        val sourceKeys = arrayOf("zonatmo", "lectormangas", "webtoons_es", "lectorxd", "olympus", "mangadex")
        val sourceCombo = createStyledComboBox(sourceOptions)

        val searchField = createStyledTextField("Buscar manga, manhwa o manhua...")
        val searchBtn = createActionButton("🔍 Buscar", Theme.ACCENT_ROSE)
        val toggleFiltersBtn = createActionButton("⚙ Filtros Avanzados ▼", Theme.BG_INPUT)

        topSearchRow.add(JLabel("Biblioteca:").apply {
            foreground = Theme.ACCENT_CYAN
            font = Font("Segoe UI", Font.BOLD, 12)
        })
        topSearchRow.add(sourceCombo)
        topSearchRow.add(JLabel("Cómics:").apply {
            foreground = Theme.TEXT_MUTED
            font = Font("Segoe UI", Font.BOLD, 13)
        })
        topSearchRow.add(searchField)
        topSearchRow.add(searchBtn)
        topSearchRow.add(toggleFiltersBtn)

        // Panel de filtros avanzados (Ocultable por defecto para no tapar el catálogo)
        val advancedFiltersPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6))
        advancedFiltersPanel.isOpaque = false
        advancedFiltersPanel.isVisible = false

        val modeOptions = arrayOf(
            "🔥 Más Populares",
            "⚡ Tiempo Real (Recién Actualizados)"
        )
        val modeCombo = createStyledComboBox(modeOptions)

        val typeOptions = arrayOf(
            "Todos (Manga/Manhwa)" to "TODOS",
            "🇰🇷 Solo Manhwa (Corea)" to "KR",
            "🇯🇵 Solo Manga (Japón)" to "JP",
            "🇨🇳 Solo Manhua (China)" to "CN"
        )
        val typeCombo = createStyledComboBox(typeOptions.map { it.first }.toTypedArray())

        val statusOptions = arrayOf(
            "Todos los Estados" to "TODOS",
            "✅ Solo Finalizados / Completos" to "completed",
            "⏳ Solo En Emisión" to "ongoing"
        )
        val statusCombo = createStyledComboBox(statusOptions.map { it.first }.toTypedArray())

        val genreCombo = createStyledComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        val sortCombo = createStyledComboBox(sortOptions.map { it.first }.toTypedArray())

        toggleFiltersBtn.addActionListener {
            advancedFiltersPanel.isVisible = !advancedFiltersPanel.isVisible
            toggleFiltersBtn.text = if (advancedFiltersPanel.isVisible) "⚙ Ocultar Filtros ▲" else "⚙ Filtros Avanzados ▼"
            filterContainer.revalidate()
            filterContainer.repaint()
        }

        advancedFiltersPanel.add(JLabel("Estado:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(statusCombo)
        advancedFiltersPanel.add(JLabel("Tipo:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(typeCombo)
        advancedFiltersPanel.add(JLabel("Modo:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(modeCombo)
        advancedFiltersPanel.add(JLabel("Género:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(genreCombo)
        advancedFiltersPanel.add(JLabel("Orden:").apply { foreground = Theme.TEXT_MUTED; font = Font("Segoe UI", Font.BOLD, 12) })
        advancedFiltersPanel.add(sortCombo)

        filterContainer.add(topSearchRow, BorderLayout.NORTH)
        filterContainer.add(advancedFiltersPanel, BorderLayout.SOUTH)

        val bottomPagination = JPanel(FlowLayout(FlowLayout.CENTER, 20, 14))
        bottomPagination.background = Theme.BG_SURFACE
        bottomPagination.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val prevBtn = createActionButton("◀ Página Anterior", Theme.BG_INPUT)
        val pageLabel = JLabel("Página 1")
        pageLabel.font = Font("Segoe UI", Font.BOLD, 14)
        pageLabel.foreground = Theme.TEXT_WHITE
        val nextBtn = createActionButton("Página Siguiente (+50 Cómics) ▶", Theme.ACCENT_ROSE)

        fun loadMangaPage(p: Int) {
            mangaPage = p.coerceAtLeast(1)
            pageLabel.text = "Página $mangaPage"
            prevBtn.isEnabled = mangaPage > 1

            val isRealtime = modeCombo.selectedIndex == 1
            val q = searchField.text.trim().let { if (it == "Buscar manga, manhwa o manhua...") "" else it }
            val selectedGenre = genresList[genreCombo.selectedIndex]
            val selectedSort = sortOptions[sortCombo.selectedIndex].second
            val selectedCountry = typeOptions[typeCombo.selectedIndex].second
            val selectedStatus = statusOptions[statusCombo.selectedIndex].second

            scope.launch {
                mangaGridPanel.removeAll()
                mangaGridPanel.add(createLoadingNotice(if (isRealtime) "Cargando capítulos recién actualizados en Español..." else "Cargando cómics oficiales en Español (Pág. $mangaPage)..."))
                mangaGridPanel.revalidate()
                mangaGridPanel.repaint()

                val results = if (isRealtime) {
                    mangaSource.getRealtimeUpdates(mangaPage)
                } else {
                    mangaSource.filter(
                        query = q,
                        genre = if (selectedGenre == "Todos") null else selectedGenre,
                        sort = selectedSort,
                        country = selectedCountry,
                        status = if (selectedStatus == "TODOS") null else selectedStatus,
                        page = mangaPage
                    )
                }
                renderMediaGrid(mangaGridPanel, results, isAnime = false)
            }
        }

        searchBtn.addActionListener { loadMangaPage(1) }
        searchField.addActionListener { loadMangaPage(1) }
        sourceCombo.addActionListener {
            mangaSource.activeSourceKey = sourceKeys[sourceCombo.selectedIndex]
            loadMangaPage(1)
        }
        modeCombo.addActionListener { loadMangaPage(1) }
        typeCombo.addActionListener { loadMangaPage(1) }
        statusCombo.addActionListener { loadMangaPage(1) }
        genreCombo.addActionListener { loadMangaPage(1) }
        sortCombo.addActionListener { loadMangaPage(1) }
        prevBtn.addActionListener { if (mangaPage > 1) loadMangaPage(mangaPage - 1) }
        nextBtn.addActionListener { loadMangaPage(mangaPage + 1) }

        bottomPagination.add(prevBtn)
        bottomPagination.add(pageLabel)
        bottomPagination.add(nextBtn)

        mangaGridPanel.isOpaque = false
        val scroll = JScrollPane(mangaGridPanel)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.border = EmptyBorder(16, 20, 16, 20)
        scroll.verticalScrollBar.unitIncrement = 25
        styleScrollBar(scroll)

        mangaPanel.add(filterContainer, BorderLayout.NORTH)
        mangaPanel.add(scroll, BorderLayout.CENTER)
        mangaPanel.add(bottomPagination, BorderLayout.SOUTH)
    }

    private fun setupExtensionsTab() {
        extensionsPanel.isOpaque = false

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 16, 14))
        topBar.background = Theme.BG_SURFACE
        topBar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR)

        val statusLabel = JLabel("Repositorios Sincronizados: Keiyoushi (1450+), Yuzono (245+) y SalmanBappi")
        statusLabel.font = Font("Segoe UI", Font.BOLD, 14)
        statusLabel.foreground = Theme.ACCENT_CYAN
        topBar.add(statusLabel)

        val jlist = JList(extensionsListModel)
        jlist.background = Theme.BG_MAIN
        jlist.foreground = Theme.TEXT_WHITE
        jlist.font = Font("Consolas", Font.PLAIN, 13)
        jlist.selectionBackground = Theme.ACCENT_PURPLE
        jlist.selectionForeground = Color.WHITE

        val scroll = JScrollPane(jlist)
        scroll.border = EmptyBorder(10, 16, 10, 16)
        styleScrollBar(scroll)

        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT, 16, 12))
        bottomBar.background = Theme.BG_SURFACE
        bottomBar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val openApkBtn = createActionButton("⬇ Descargar APK de Extensión", Theme.ACCENT_PURPLE)
        openApkBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                JOptionPane.showMessageDialog(this, "Extensión seleccionada:\n$selected\n\nDescargando archivo APK...", "Gestor de Extensiones", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        bottomBar.add(openApkBtn)

        extensionsPanel.add(topBar, BorderLayout.NORTH)
        extensionsPanel.add(scroll, BorderLayout.CENTER)
        extensionsPanel.add(bottomBar, BorderLayout.SOUTH)
    }

    private fun setupDownloadsTab() {
        downloadsPanel.isOpaque = false

        val topBar = JPanel(BorderLayout(16, 10))
        topBar.background = Theme.BG_SURFACE
        topBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(12, 20, 12, 20)
        )

        val infoLabel = JLabel("<html><font color='#ffffff' size='4'><b>Gestor de Contenido Offline</b></font><br><font color='#a0a5b9'>Lee tus mangas y mira tus animes guardados 100% sin conexión</font></html>")
        val refreshBtn = createActionButton("🔄 Actualizar", Theme.BG_INPUT)

        topBar.add(infoLabel, BorderLayout.WEST)
        topBar.add(refreshBtn, BorderLayout.EAST)

        val list = JList(downloadsListModel)
        list.background = Theme.BG_MAIN
        list.foreground = Theme.TEXT_WHITE
        list.font = Font("Segoe UI", Font.PLAIN, 14)
        list.selectionBackground = Theme.ACCENT_PURPLE
        list.selectionForeground = Color.WHITE

        val scroll = JScrollPane(list)
        scroll.border = EmptyBorder(10, 16, 10, 16)
        styleScrollBar(scroll)

        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT, 14, 12))
        bottomBar.background = Theme.BG_SURFACE
        bottomBar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val openOfflineBtn = createActionButton("▶ Abrir Seleccionado Offline", Theme.ACCENT_PURPLE)
        val deleteOfflineBtn = createActionButton("🗑 Eliminar", Theme.BG_INPUT)
        val openFolderBtn = createActionButton("📂 Abrir Carpeta", Theme.ACCENT_ROSE)

        refreshBtn.addActionListener { refreshOfflineDownloads() }

        openOfflineBtn.addActionListener {
            val idx = list.selectedIndex
            if (idx in offlineMediaList.indices) {
                val item = offlineMediaList[idx]
                if (item.type == ContentType.ANIME) {
                    launchHtml5Player(item.title, item.subTitle, item.localFileOrDir.toURI().toString())
                } else {
                    val pageFiles = item.localFileOrDir.listFiles()
                        ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
                        ?.sortedBy { it.name }
                        ?: emptyList()
                    val pages = pageFiles.mapIndexed { pIdx, f ->
                        MangaPage(index = pIdx + 1, imageUrl = f.toURI().toString())
                    }
                    if (pages.isNotEmpty()) {
                        openMangaReader(
                            MediaItem(id = item.id, title = item.title, url = "", coverUrl = "", type = ContentType.MANGA),
                            EpisodeOrChapter(id = item.id, name = item.subTitle, number = 1f, url = ""),
                            pages
                        )
                    } else {
                        JOptionPane.showMessageDialog(this, "No se encontraron páginas en este capítulo.", "Aviso", JOptionPane.WARNING_MESSAGE)
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Selecciona un elemento de la lista para abrirlo sin internet.")
            }
        }

        deleteOfflineBtn.addActionListener {
            val idx = list.selectedIndex
            if (idx in offlineMediaList.indices) {
                val item = offlineMediaList[idx]
                val confirm = JOptionPane.showConfirmDialog(this, "¿Deseas eliminar '${item.title} - ${item.subTitle}' del disco?", "Confirmar Eliminación", JOptionPane.YES_NO_OPTION)
                if (confirm == JOptionPane.YES_OPTION) {
                    offlineStorage.deleteOfflineItem(item)
                    refreshOfflineDownloads()
                }
            }
        }

        openFolderBtn.addActionListener {
            try {
                Desktop.getDesktop().open(offlineStorage.baseDir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        bottomBar.add(deleteOfflineBtn)
        bottomBar.add(openFolderBtn)
        bottomBar.add(openOfflineBtn)

        downloadsPanel.add(topBar, BorderLayout.NORTH)
        downloadsPanel.add(scroll, BorderLayout.CENTER)
        downloadsPanel.add(bottomBar, BorderLayout.SOUTH)
    }

    private fun refreshOfflineDownloads() {
        scope.launch(Dispatchers.IO) {
            val mangas = offlineStorage.getDownloadedMangas()
            val animes = offlineStorage.getDownloadedAnimes()
            withContext(Dispatchers.Swing) {
                offlineMediaList.clear()
                downloadsListModel.clear()
                for (m in mangas) {
                    offlineMediaList.add(m)
                    downloadsListModel.addElement("[📖 MANGA] ${m.title} • ${m.subTitle} (${m.pageCountOrSize})")
                }
                for (a in animes) {
                    offlineMediaList.add(a)
                    downloadsListModel.addElement("[🎬 ANIME] ${a.title} • ${a.subTitle} (${a.pageCountOrSize})")
                }
                if (offlineMediaList.isEmpty()) {
                    downloadsListModel.addElement("No tienes descargas aún. Descarga cualquier anime o manga para disfrutarlo aquí sin internet.")
                }
            }
        }
    }

    private fun checkForUpdates(forceManualCheck: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val update = AppUpdateManager.checkLatestRelease(forceManualCheck = forceManualCheck)
            withContext(Dispatchers.Swing) {
                if (update.hasUpdate) {
                    showDesktopUpdateDialog(update)
                } else if (forceManualCheck) {
                    JOptionPane.showMessageDialog(
                        this@AniyomiDesktopApp,
                        "¡Estás al día!\nTienes instalada la versión más reciente (v${AppUpdateManager.CURRENT_VERSION}).",
                        "Aniyomi Cross - Actualizaciones",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }
    }

    private fun showDesktopUpdateDialog(update: AppUpdateManager.UpdateInfo) {
        val dialog = JDialog(this, "Nueva Actualización Disponible - Aniyomi Cross", true)
        dialog.setSize(560, 380)
        dialog.setLocationRelativeTo(this)
        dialog.contentPane.background = Theme.BG_SURFACE
        dialog.layout = BorderLayout(15, 15)

        val main = JPanel(BorderLayout(14, 14))
        main.border = EmptyBorder(22, 24, 22, 24)
        main.isOpaque = false

        val title = JLabel("<html><font color='#ffffff' size='5'><b>¡Nueva versión disponible!</b></font><br>" +
                "<font color='#a78bfa' size='4'>Versión actual: <b>v${update.currentVersion}</b> &nbsp;➔&nbsp; Nueva versión: <font color='#22c55e'><b>v${update.latestVersion}</b></font></font></html>")
        val notes = JTextArea(update.releaseNotes)
        notes.isEditable = false
        notes.background = Theme.BG_INPUT
        notes.foreground = Theme.TEXT_MUTED
        notes.font = Font("Segoe UI", Font.PLAIN, 12)
        notes.lineWrap = true
        notes.wrapStyleWord = true

        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        btnRow.isOpaque = false
        val cancelBtn = createActionButton("Más tarde", Theme.BG_INPUT)
        val updateBtn = createActionButton("⬇ Instalar v${update.latestVersion}", Theme.ACCENT_ROSE)

        cancelBtn.addActionListener {
            AppUpdateManager.dismissVersion(update.latestVersion)
            dialog.dispose()
        }
        updateBtn.addActionListener {
            val url = update.jarDownloadUrl ?: "https://github.com/Gabriel48772/aniyomi-cross/raw/main/dist/AniyomiCross-PC.jar"
            updateBtn.isEnabled = false
            updateBtn.text = "Descargando..."
            scope.launch(Dispatchers.IO) {
                val currentJar = try {
                    File(AniyomiDesktopApp::class.java.protectionDomain.codeSource.location.toURI().path)
                } catch (e: Exception) {
                    File("dist/AniyomiCross-PC.jar")
                }
                val updateJar = File(currentJar.parentFile ?: File("."), "AniyomiCross-PC-update.jar")
                val success = AppUpdateManager.downloadUpdate(url, updateJar) { p ->
                    SwingUtilities.invokeLater {
                        updateBtn.text = "Descargando ${(p * 100).toInt()}%..."
                    }
                }
                withContext(Dispatchers.Swing) {
                    if (success && updateJar.exists()) {
                        AppUpdateManager.markVersionInstalled(update.latestVersion)
                        JOptionPane.showMessageDialog(dialog, "¡Actualización a v${update.latestVersion} descargada con éxito!\nLa aplicación se reiniciará con las mejoras.")
                        dialog.dispose()

                        // Crear script batch para reemplazar y reiniciar sin bloqueo de archivo en Windows
                        val batFile = File(currentJar.parentFile ?: File("."), "apply_update.bat")
                        batFile.writeText(
                            "@echo off\r\n" +
                            "timeout /t 1 /nobreak >nul\r\n" +
                            "move /y \"${updateJar.absolutePath}\" \"${currentJar.absolutePath}\"\r\n" +
                            "start \"\" javaw -jar \"${currentJar.absolutePath}\"\r\n" +
                            "del \"%~f0\"\r\n"
                        )
                        Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "/min", batFile.absolutePath))
                        System.exit(0)
                    } else {
                        updateBtn.isEnabled = true
                        updateBtn.text = "Reintentar Descarga"
                    }
                }
            }
        }

        btnRow.add(cancelBtn)
        btnRow.add(updateBtn)

        main.add(title, BorderLayout.NORTH)
        main.add(JScrollPane(notes), BorderLayout.CENTER)
        main.add(btnRow, BorderLayout.SOUTH)

        dialog.contentPane.add(main)
        dialog.isVisible = true
    }

    private fun loadInitialData() {
        scope.launch {
            val animes = animeSource.getPopular(1)
            renderMediaGrid(animeGridPanel, animes, isAnime = true)

            val mangas = mangaSource.getPopular(1)
            renderMediaGrid(mangaGridPanel, mangas, isAnime = false)

            val extList = repoManager.fetchAllExtensions()
            extensionsListModel.clear()
            for (ext in extList.take(500)) {
                extensionsListModel.addElement("[${ext.type}] [${ext.lang.uppercase()}] ${ext.name} (v${ext.version}) • ${ext.pkg}")
            }
        }
    }

    // ==========================================
    // RENDERIZADO DE TARJETAS PREMIUM DE PELÍCULAS/CÓMICS
    // ==========================================
    private fun renderMediaGrid(panel: JPanel, items: List<MediaItem>, isAnime: Boolean) {
        panel.removeAll()
        if (items.isEmpty()) {
            panel.add(createLoadingNotice("No se encontraron resultados con los filtros actuales."))
            panel.revalidate()
            panel.repaint()
            return
        }

        for (item in items) {
            val card = ModernMediaCard(item, isAnime) {
                showDetailsDialog(item, isAnime)
            }
            panel.add(card)
        }
        panel.revalidate()
        panel.repaint()
    }

    // ==========================================
    // DIÁLOGO DE DETALLES Y EPISODIOS MODERNO
    // ==========================================
    private fun showDetailsDialog(item: MediaItem, isAnime: Boolean) {
        val dialog = JDialog(this, item.title, true)
        dialog.setSize(920, 720)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout()
        dialog.contentPane.background = Theme.BG_MAIN

        val mainBox = JPanel(BorderLayout(16, 16))
        mainBox.border = EmptyBorder(20, 24, 20, 24)
        mainBox.isOpaque = false

        // Cabecera Hero con información estilizada
        val heroPanel = JPanel(BorderLayout(20, 10))
        heroPanel.background = Theme.BG_SURFACE
        heroPanel.border = BorderFactory.createCompoundBorder(
            RoundedBorder(Theme.BORDER_COLOR, 14, 1),
            EmptyBorder(16, 18, 16, 18)
        )

        val posterLbl = JLabel()
        posterLbl.preferredSize = Dimension(140, 200)
        posterLbl.background = Theme.BG_INPUT
        posterLbl.isOpaque = true
        posterLbl.horizontalAlignment = SwingConstants.CENTER

        scope.launch(Dispatchers.IO) {
            try {
                val img = ImageIO.read(URL(item.coverUrl))
                if (img != null) {
                    val scaled = img.getScaledInstance(140, 200, Image.SCALE_SMOOTH)
                    withContext(Dispatchers.Swing) {
                        posterLbl.icon = ImageIcon(scaled)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val infoCol = JPanel()
        infoCol.layout = BoxLayout(infoCol, BoxLayout.Y_AXIS)
        infoCol.isOpaque = false

        val titleLbl = JLabel(item.title)
        titleLbl.font = Font("Segoe UI", Font.BOLD, 20)
        titleLbl.foreground = Theme.TEXT_WHITE

        val badgeRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        badgeRow.isOpaque = false

        val statusBadge = JLabel(" ${item.status} ")
        statusBadge.font = Font("Segoe UI", Font.BOLD, 11)
        statusBadge.foreground = Theme.ACCENT_CYAN
        statusBadge.border = RoundedBorder(Theme.ACCENT_CYAN, 8, 1)

        val typeBadge = JLabel(if (isAnime) " SERIE ANIME " else " CÓMIC / MANGA ")
        typeBadge.font = Font("Segoe UI", Font.BOLD, 11)
        typeBadge.foreground = if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE
        typeBadge.border = RoundedBorder(if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE, 8, 1)

        badgeRow.add(statusBadge)
        badgeRow.add(typeBadge)

        val genresLbl = JLabel("Géneros: " + item.genres.joinToString(" • "))
        genresLbl.font = Font("Segoe UI", Font.PLAIN, 12)
        genresLbl.foreground = Theme.TEXT_MUTED

        val descArea = JTextArea(item.description.ifEmpty { "Sin descripción disponible." })
        descArea.isEditable = false
        descArea.lineWrap = true
        descArea.wrapStyleWord = true
        descArea.font = Font("Segoe UI", Font.PLAIN, 13)
        descArea.foreground = Theme.TEXT_WHITE
        descArea.background = Theme.BG_SURFACE
        val descScroll = JScrollPane(descArea)
        descScroll.border = EmptyBorder(6, 0, 0, 0)
        descScroll.isOpaque = false
        descScroll.viewport.isOpaque = false
        descScroll.preferredSize = Dimension(540, 95)
        styleScrollBar(descScroll)

        infoCol.add(titleLbl)
        infoCol.add(Box.createVerticalStrut(6))
        infoCol.add(badgeRow)
        infoCol.add(Box.createVerticalStrut(6))
        infoCol.add(genresLbl)
        infoCol.add(Box.createVerticalStrut(8))
        infoCol.add(descScroll)

        heroPanel.add(posterLbl, BorderLayout.WEST)
        heroPanel.add(infoCol, BorderLayout.CENTER)

        // Lista de Episodios / Capítulos Estilizada
        val listModel = DefaultListModel<EpisodeOrChapter>()
        val jlist = JList(listModel)
        jlist.background = Theme.BG_SURFACE
        jlist.foreground = Theme.TEXT_WHITE
        jlist.font = Font("Segoe UI", Font.PLAIN, 14)
        jlist.selectionBackground = if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE
        jlist.selectionForeground = Color.WHITE

        jlist.cellRenderer = ListCellRenderer { _, value, _, isSelected, _ ->
            val p = JPanel(BorderLayout(10, 0))
            p.border = EmptyBorder(10, 16, 10, 16)
            p.background = if (isSelected) (if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE) else Theme.BG_SURFACE

            val icon = JLabel(if (isAnime) "▶" else "📖")
            icon.foreground = if (isSelected) Color.WHITE else Theme.ACCENT_CYAN
            icon.font = Font("Segoe UI", Font.BOLD, 14)

            val text = JLabel(value.name)
            text.foreground = Color.WHITE
            text.font = Font("Segoe UI", Font.PLAIN, 14)

            p.add(icon, BorderLayout.WEST)
            p.add(text, BorderLayout.CENTER)
            p
        }

        val listScroll = JScrollPane(jlist)
        listScroll.border = RoundedBorder(Theme.BORDER_COLOR, 14, 1)
        styleScrollBar(listScroll)

        // Botones de Acción
        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 10))
        btnPanel.isOpaque = false

        val actionBtn = createActionButton(
            if (isAnime) "▶ Ver Episodio Seleccionado" else "📖 Leer Capítulo Seleccionado",
            if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE
        )
        actionBtn.font = Font("Segoe UI", Font.BOLD, 14)
        actionBtn.margin = Insets(8, 22, 8, 22)

        val downloadBtn = createActionButton("⬇ Descargar Offline ▾", Theme.BG_INPUT)
        downloadBtn.margin = Insets(8, 16, 8, 16)

        // Menú desplegable para Descarga Masiva y Selección
        val downloadMenu = JPopupMenu()
        downloadMenu.background = Theme.BG_SURFACE
        downloadMenu.border = RoundedBorder(Theme.BORDER_COLOR, 8, 1)

        val itemSingle = JMenuItem("⬇ Descargar capítulo/episodio seleccionado").apply {
            background = Theme.BG_SURFACE
            foreground = Theme.TEXT_WHITE
            font = Font("Segoe UI", Font.BOLD, 12)
        }
        val itemAll = JMenuItem("📦 Descargar TODOS los capítulos (Descarga masiva)").apply {
            background = Theme.BG_SURFACE
            foreground = Theme.ACCENT_ROSE
            font = Font("Segoe UI", Font.BOLD, 12)
        }
        val itemSelect = JMenuItem("☑ Seleccionar múltiples capítulos para descarga masiva...").apply {
            background = Theme.BG_SURFACE
            foreground = Theme.ACCENT_CYAN
            font = Font("Segoe UI", Font.BOLD, 12)
        }

        downloadMenu.add(itemSingle)
        downloadMenu.add(itemAll)
        downloadMenu.add(itemSelect)

        itemSingle.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                downloadChaptersInBatch(dialog, item, isAnime, listOf(selected), downloadBtn)
            } else {
                if (listModel.size() > 0) {
                    downloadChaptersInBatch(dialog, item, isAnime, listOf(listModel.getElementAt(0)), downloadBtn)
                } else {
                    JOptionPane.showMessageDialog(dialog, "No hay capítulos disponibles para descargar.")
                }
            }
        }

        itemAll.addActionListener {
            val allChapters = (0 until listModel.size()).map { listModel.getElementAt(it) }
            if (allChapters.isNotEmpty()) {
                val confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "¿Deseas descargar los ${allChapters.size} capítulos en lote para verlos sin internet?",
                    "Confirmar Descarga Masiva",
                    JOptionPane.YES_NO_OPTION
                )
                if (confirm == JOptionPane.YES_OPTION) {
                    downloadChaptersInBatch(dialog, item, isAnime, allChapters, downloadBtn)
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "No hay capítulos cargados para descargar.")
            }
        }

        itemSelect.addActionListener {
            val allChapters = (0 until listModel.size()).map { listModel.getElementAt(it) }
            if (allChapters.isNotEmpty()) {
                showBatchSelectionDialog(dialog, item, isAnime, allChapters, downloadBtn)
            } else {
                JOptionPane.showMessageDialog(dialog, "No hay capítulos disponibles para seleccionar.")
            }
        }

        downloadBtn.addActionListener {
            downloadMenu.show(downloadBtn, 0, downloadBtn.height)
        }

        fun triggerOpenSelected() {
            val selected = jlist.selectedValue ?: if (listModel.size() > 0) listModel.getElementAt(0) else null
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "No hay ningún capítulo o episodio seleccionado.")
                return
            }
            if (isAnime) {
                actionBtn.isEnabled = false
                actionBtn.text = "Buscando servidores..."
                scope.launch {
                    val videos = animeSource.getVideoList(selected)
                    withContext(Dispatchers.Swing) {
                        actionBtn.isEnabled = true
                        actionBtn.text = "▶ Ver Episodio Seleccionado"
                        if (videos.isNotEmpty() && videos.first().videoUrl.isNotBlank()) {
                            openAnimePlayer(item, selected, videos)
                        } else {
                            JOptionPane.showMessageDialog(
                                dialog,
                                "No se encontraron servidores activos para este episodio.\nPuede estar en proceso de subida o en sincronización.",
                                "Aviso de Servidor",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }
                    }
                }
            } else {
                actionBtn.isEnabled = false
                actionBtn.text = "Cargando páginas..."
                scope.launch {
                    val pages = mangaSource.getPageList(selected)
                    withContext(Dispatchers.Swing) {
                        actionBtn.isEnabled = true
                        actionBtn.text = "📖 Leer Capítulo Seleccionado"
                        if (pages.isNotEmpty()) {
                            openMangaReader(item, selected, pages)
                        } else {
                            JOptionPane.showMessageDialog(
                                dialog,
                                "No se pudieron cargar las páginas de este capítulo.",
                                "Aviso",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }
                    }
                }
            }
        }

        actionBtn.addActionListener { triggerOpenSelected() }
        jlist.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    triggerOpenSelected()
                }
            }
        })

        btnPanel.add(downloadBtn)
        btnPanel.add(actionBtn)

        mainBox.add(heroPanel, BorderLayout.NORTH)
        mainBox.add(listScroll, BorderLayout.CENTER)
        mainBox.add(btnPanel, BorderLayout.SOUTH)

        dialog.contentPane.add(mainBox)

        scope.launch {
            val pair = if (isAnime) animeSource.getDetails(item) else mangaSource.getDetails(item)
            listModel.clear()
            for (ep in pair.second) {
                listModel.addElement(ep)
            }
        }

        dialog.isVisible = true
    }

    private fun downloadChaptersInBatch(
        parentComponent: Component,
        media: MediaItem,
        isAnime: Boolean,
        targets: List<EpisodeOrChapter>,
        downloadBtn: JButton
    ) {
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "No hay capítulos seleccionados para descargar.")
            return
        }

        downloadBtn.isEnabled = false
        val total = targets.size

        scope.launch(Dispatchers.IO) {
            var downloadedCount = 0
            for ((idx, ch) in targets.withIndex()) {
                withContext(Dispatchers.Swing) {
                    downloadBtn.text = "Descargando (${idx + 1}/$total)..."
                }
                try {
                    if (isAnime) {
                        val videos = animeSource.getVideoList(ch)
                        if (videos.isNotEmpty()) {
                            offlineStorage.downloadAnimeEpisode(media, ch, videos.first().videoUrl) { }
                            downloadedCount++
                        }
                    } else {
                        val pages = mangaSource.getPageList(ch)
                        if (pages.isNotEmpty()) {
                            offlineStorage.downloadMangaChapter(media, ch, pages) { }
                            downloadedCount++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Swing) {
                downloadBtn.isEnabled = true
                downloadBtn.text = "⬇ Descargar Offline ▾"
                refreshOfflineDownloads()
                JOptionPane.showMessageDialog(
                    parentComponent,
                    "¡Descarga masiva completada!\nSe guardaron con éxito $downloadedCount de $total capítulos para uso sin conexión.",
                    "Descarga Masiva Completa",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
    }

    private fun showBatchSelectionDialog(
        parent: JDialog,
        media: MediaItem,
        isAnime: Boolean,
        allChapters: List<EpisodeOrChapter>,
        downloadBtn: JButton
    ) {
        val selDialog = JDialog(parent, "Selección múltiple para descarga masiva - ${media.title}", true)
        selDialog.setSize(520, 560)
        selDialog.setLocationRelativeTo(parent)
        selDialog.contentPane.background = Theme.BG_SURFACE
        selDialog.layout = BorderLayout(10, 10)

        val topHeader = JPanel(BorderLayout(8, 8))
        topHeader.isOpaque = false
        topHeader.border = EmptyBorder(12, 16, 8, 16)
        val headerLbl = JLabel("Selecciona los capítulos que deseas descargar:")
        headerLbl.foreground = Color.WHITE
        headerLbl.font = Font("Segoe UI", Font.BOLD, 13)

        val selectButtonsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        selectButtonsRow.isOpaque = false
        val btnSelectAll = createActionButton("Todos", Theme.BG_INPUT)
        val btnDeselectAll = createActionButton("Ninguno", Theme.BG_INPUT)
        selectButtonsRow.add(btnSelectAll)
        selectButtonsRow.add(btnDeselectAll)

        topHeader.add(headerLbl, BorderLayout.WEST)
        topHeader.add(selectButtonsRow, BorderLayout.EAST)

        val checkListPanel = JPanel()
        checkListPanel.layout = BoxLayout(checkListPanel, BoxLayout.Y_AXIS)
        checkListPanel.background = Theme.BG_MAIN
        checkListPanel.border = EmptyBorder(10, 16, 10, 16)

        val checkBoxes = mutableListOf<Pair<JCheckBox, EpisodeOrChapter>>()
        for (ch in allChapters) {
            val cb = JCheckBox(ch.name, true)
            cb.isOpaque = false
            cb.foreground = Theme.TEXT_WHITE
            cb.font = Font("Segoe UI", Font.PLAIN, 12)
            checkBoxes.add(Pair(cb, ch))
            checkListPanel.add(cb)
            checkListPanel.add(Box.createVerticalStrut(4))
        }

        btnSelectAll.addActionListener { checkBoxes.forEach { it.first.isSelected = true } }
        btnDeselectAll.addActionListener { checkBoxes.forEach { it.first.isSelected = false } }

        val scroll = JScrollPane(checkListPanel)
        scroll.border = BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER_COLOR)
        scroll.verticalScrollBar.unitIncrement = 25
        styleScrollBar(scroll)

        val bottomRow = JPanel(FlowLayout(FlowLayout.RIGHT, 12, 12))
        bottomRow.isOpaque = false
        val cancelBtn = createActionButton("Cancelar", Theme.BG_INPUT)
        val startDownloadBtn = createActionButton("⬇ Descargar Seleccionados", Theme.ACCENT_ROSE)

        cancelBtn.addActionListener { selDialog.dispose() }
        startDownloadBtn.addActionListener {
            val selected = checkBoxes.filter { it.first.isSelected }.map { it.second }
            selDialog.dispose()
            downloadChaptersInBatch(parent, media, isAnime, selected, downloadBtn)
        }

        bottomRow.add(cancelBtn)
        bottomRow.add(startDownloadBtn)

        selDialog.contentPane.add(topHeader, BorderLayout.NORTH)
        selDialog.contentPane.add(scroll, BorderLayout.CENTER)
        selDialog.contentPane.add(bottomRow, BorderLayout.SOUTH)
        selDialog.isVisible = true
    }

    private fun openAnimePlayer(media: MediaItem, episode: EpisodeOrChapter, videos: List<VideoSource>) {
        val playerDialog = JDialog(this, "Reproductor Cinema - ${media.title}", true)
        playerDialog.setSize(780, 500)
        playerDialog.setLocationRelativeTo(this)
        playerDialog.contentPane.background = Theme.BG_MAIN
        playerDialog.layout = BorderLayout()

        val content = JPanel(BorderLayout(15, 15))
        content.border = EmptyBorder(24, 28, 24, 28)
        content.isOpaque = false

        val titleLbl = JLabel("<html><font color='#ffffff' size='5'><b>${media.title}</b></font><br><font color='#8b5cf6' size='3'>${episode.name}</font></html>")
        content.add(titleLbl, BorderLayout.NORTH)

        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)
        centerPanel.isOpaque = false

        val infoLabel = JLabel("<html><font color='#00f0ff'><b>Servidores Disponibles en Español:</b></font><br><font color='#a0a5b9'>Selecciona cómo deseas disfrutar este episodio:</font></html>")
        centerPanel.add(infoLabel)
        centerPanel.add(Box.createVerticalStrut(12))

        val scrollServerPanel = JPanel(GridLayout(0, 1, 8, 8))
        scrollServerPanel.isOpaque = false

        for (v in videos) {
            val serverRow = JPanel(BorderLayout(8, 0))
            serverRow.background = Theme.BG_SURFACE
            serverRow.border = BorderFactory.createCompoundBorder(
                RoundedBorder(Theme.BORDER_COLOR, 10, 1),
                EmptyBorder(8, 12, 8, 12)
            )

            val serverTitle = JLabel(v.quality)
            serverTitle.foreground = Color.WHITE
            serverTitle.font = Font("Segoe UI", Font.BOLD, 13)

            val actionBtns = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
            actionBtns.isOpaque = false

            val playBtn = createActionButton("▶ Ver", Theme.ACCENT_PURPLE)
            playBtn.addActionListener {
                launchHtml5Player(media.title, episode.name, v.videoUrl)
            }

            val vlcBtn = createActionButton("🎬 VLC", Theme.BG_INPUT)
            vlcBtn.toolTipText = "Reproducir directamente en VLC Media Player"
            vlcBtn.addActionListener {
                val launched = launchExternalVlc(v.videoUrl)
                if (!launched) {
                    val sel = StringSelection(v.videoUrl)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    JOptionPane.showMessageDialog(playerDialog, "No se encontró VLC instalado en la ruta por defecto.\nSe ha copiado el enlace directo al portapapeles para pegarlo en tu reproductor (Ctrl+V).")
                }
            }

            actionBtns.add(playBtn)
            actionBtns.add(vlcBtn)

            serverRow.add(serverTitle, BorderLayout.CENTER)
            serverRow.add(actionBtns, BorderLayout.EAST)
            scrollServerPanel.add(serverRow)
        }

        val scroll = JScrollPane(scrollServerPanel)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false
        scroll.border = EmptyBorder(0, 0, 0, 0)
        styleScrollBar(scroll)
        centerPanel.add(scroll)

        val bottomRow = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 8))
        bottomRow.isOpaque = false

        val copyLinkBtn = createActionButton("📋 Copiar Enlace Directo", Theme.BG_INPUT)
        copyLinkBtn.addActionListener {
            val firstUrl = videos.firstOrNull()?.videoUrl ?: ""
            if (firstUrl.isNotBlank()) {
                val sel = StringSelection(firstUrl)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                JOptionPane.showMessageDialog(playerDialog, "¡Enlace copiado al portapapeles!")
            }
        }
        bottomRow.add(copyLinkBtn)

        content.add(centerPanel, BorderLayout.CENTER)
        content.add(bottomRow, BorderLayout.SOUTH)
        playerDialog.contentPane.add(content)
        playerDialog.isVisible = true
    }

    private fun launchExternalVlc(videoUrl: String): Boolean {
        val vlcCandidates = listOf(
            "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
            "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
            "vlc"
        )
        for (candidate in vlcCandidates) {
            val f = File(candidate)
            if (f.exists() || candidate == "vlc") {
                return try {
                    ProcessBuilder(candidate, videoUrl).start()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return false
    }

    private fun launchHtml5Player(title: String, episodeName: String, videoUrl: String) {
        try {
            // Si es un iframe embebido directo (ej. VOE, Filemoon, JKanime player, Streamwish)
            if (videoUrl.contains("voe.sx") || videoUrl.contains("filemoon") || videoUrl.contains("dood") ||
                videoUrl.contains("streamwish") || videoUrl.contains("mp4upload") || videoUrl.contains("jkplayer")) {
                // Abrir directamente en el navegador predeterminado para evitar bloqueos por iframe sandbox
                Desktop.getDesktop().browse(URI.create(videoUrl))
                return
            }

            // Reproductor integrado local sin bloqueos de CORS
            val html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <title>$title - $episodeName</title>
                    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                    <style>
                        body { margin: 0; background: #0a0b10; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; }
                        .header { width: 92%; max-width: 1100px; padding: 14px 0; display: flex; justify-content: space-between; align-items: center; }
                        h1 { margin: 0; font-size: 22px; color: #8b5cf6; }
                        span { color: #a0a5b9; font-size: 15px; }
                        .player-box { width: 92%; max-width: 1100px; background: #000; border-radius: 14px; overflow: hidden; box-shadow: 0 20px 50px rgba(0,0,0,0.9); border: 1px solid #282a40; position: relative; }
                        video { width: 100%; height: auto; max-height: 82vh; display: block; outline: none; }
                        .footer { margin-top: 15px; color: #6b7280; font-size: 13px; display: flex; gap: 15px; align-items: center; }
                        .btn { background: #1f2238; border: 1px solid #33385a; color: #fff; padding: 6px 14px; border-radius: 8px; cursor: pointer; text-decoration: none; font-size: 13px; font-weight: bold; }
                        .btn:hover { background: #8b5cf6; border-color: #8b5cf6; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>$title</h1>
                        <span>$episodeName</span>
                    </div>
                    <div class="player-box">
                        <video id="video-element" controls autoplay playsinline crossorigin="anonymous">
                            <source src="$videoUrl" type="video/mp4">
                            Tu navegador no soporta video directo.
                        </video>
                    </div>
                    <div class="footer">
                        <span>Aniyomi Cross • Cinema Player</span>
                        <button class="btn" onclick="video.currentTime -= 10">⏪ -10s</button>
                        <button class="btn" onclick="video.currentTime += 10">⏩ +10s</button>
                        <button class="btn" onclick="toggleFs()">⛶ Pantalla Completa</button>
                    </div>
                    <script>
                        const video = document.getElementById('video-element');
                        const url = '$videoUrl';
                        if (url.includes('.m3u8')) {
                            if (Hls.isSupported()) {
                                const hls = new Hls({ enableWorker: true });
                                hls.loadSource(url);
                                hls.attachMedia(video);
                                hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                    video.play().catch(e => console.log('Autoplay'));
                                });
                            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                video.src = url;
                                video.play().catch(e => console.log('Autoplay'));
                            }
                        }
                        function toggleFs() {
                            if (!document.fullscreenElement) video.requestFullscreen();
                            else document.exitFullscreen();
                        }
                        window.addEventListener('keydown', (e) => {
                            if (e.code === 'Space') { e.preventDefault(); video.paused ? video.play() : video.pause(); }
                            if (e.code === 'ArrowLeft') { video.currentTime -= 10; }
                            if (e.code === 'ArrowRight') { video.currentTime += 10; }
                            if (e.code === 'KeyF') { toggleFs(); }
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()

            val tempFile = File(System.getProperty("java.io.tmpdir"), "aniyomi_player.html")
            tempFile.writeText(html, Charsets.UTF_8)
            Desktop.getDesktop().browse(tempFile.toURI())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchImageFromNetwork(imageUrl: String): Image? {
        return try {
            val ref = when {
                imageUrl.contains("webtoon", ignoreCase = true) -> "https://www.webtoons.com/"
                imageUrl.contains("zonatmo", ignoreCase = true) || imageUrl.contains("storage", ignoreCase = true) -> "https://zonatmo.org/"
                else -> "https://mangadex.org/"
            }
            val req = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Referer", ref)
                .build()
            com.aniyomi.cross.network.NetworkClient.okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val bytes = res.body?.bytes()
                    if (bytes != null) ImageIO.read(bytes.inputStream()) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaReader(media: MediaItem, chapter: EpisodeOrChapter, pages: List<MangaPage>) {
        val readerDialog = JDialog(this, "Lector Aniyomi - ${media.title} (${chapter.name})", true)
        readerDialog.setSize(1020, 960)
        readerDialog.setLocationRelativeTo(this)
        readerDialog.contentPane.background = Color.BLACK
        readerDialog.layout = BorderLayout()

        var currentPageIndex = 0
        var isWebtoonMode = false

        val topBar = JPanel(BorderLayout())
        topBar.background = Color(16, 17, 24)
        topBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(10, 20, 10, 20)
        )

        val titleLbl = JLabel("<html><font color='#ffffff'><b>${media.title}</b></font> <font color='#8b5cf6'>• ${chapter.name}</font></html>")
        titleLbl.font = Font("Segoe UI", Font.BOLD, 14)

        val pageStatus = JLabel("Página 1 de ${pages.size}")
        pageStatus.font = Font("Segoe UI", Font.BOLD, 13)
        pageStatus.foreground = Theme.ACCENT_CYAN

        topBar.add(titleLbl, BorderLayout.WEST)
        topBar.add(pageStatus, BorderLayout.EAST)

        // Contenedor CardLayout central: Alterna Modo Página y Cascada Webtoon en la MISMA ventana
        val cardLayout = CardLayout()
        val centerCardPanel = JPanel(cardLayout)
        centerCardPanel.background = Color.BLACK

        // --- MODO 1: LECTOR PÁGINA A PÁGINA ---
        val imageLabel = JLabel("Cargando página...", SwingConstants.CENTER)
        imageLabel.background = Color.BLACK
        imageLabel.isOpaque = true
        val pageScrollPane = JScrollPane(imageLabel)
        pageScrollPane.border = null
        pageScrollPane.verticalScrollBar.unitIncrement = 35
        styleScrollBar(pageScrollPane)
        centerCardPanel.add(pageScrollPane, "PAGE")

        // --- MODO 2: CASCADA CONTINUA WEBTOON (EN LA MISMA HOJA) ---
        val webtoonContainer = JPanel()
        webtoonContainer.layout = BoxLayout(webtoonContainer, BoxLayout.Y_AXIS)
        webtoonContainer.background = Color.BLACK
        val webtoonScrollPane = JScrollPane(webtoonContainer)
        webtoonScrollPane.border = null
        webtoonScrollPane.verticalScrollBar.unitIncrement = 45
        styleScrollBar(webtoonScrollPane)
        centerCardPanel.add(webtoonScrollPane, "WEBTOON")

        // Barra inferior estilo Aniyomi con Slider interactivo y botones
        val bottomBar = JPanel(BorderLayout(15, 8))
        bottomBar.background = Color(16, 17, 24)
        bottomBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR),
            EmptyBorder(8, 20, 12, 20)
        )

        val pageSlider = JSlider(1, pages.size.coerceAtLeast(1), 1)
        pageSlider.background = Color(16, 17, 24)
        pageSlider.foreground = Theme.ACCENT_PURPLE
        pageSlider.isOpaque = false

        val controlsRow = JPanel(FlowLayout(FlowLayout.CENTER, 14, 0))
        controlsRow.isOpaque = false

        val prevBtn = createActionButton("◀ Anterior (A)", Theme.BG_INPUT)
        val nextBtn = createActionButton("Siguiente ▶ (D)", Theme.ACCENT_ROSE)
        val webtoonToggleBtn = createActionButton("📜 Cascada Webtoon (W)", Theme.ACCENT_PURPLE)

        controlsRow.add(prevBtn)
        controlsRow.add(nextBtn)
        controlsRow.add(webtoonToggleBtn)

        bottomBar.add(pageSlider, BorderLayout.NORTH)
        bottomBar.add(controlsRow, BorderLayout.SOUTH)

        var isUpdatingSlider = false

        fun showPage(idx: Int) {
            if (pages.isEmpty()) return
            currentPageIndex = idx.coerceIn(0, pages.size - 1)
            pageStatus.text = "Página ${currentPageIndex + 1} de ${pages.size}"
            prevBtn.isEnabled = currentPageIndex > 0
            nextBtn.isEnabled = currentPageIndex < pages.size - 1

            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                pageSlider.value = currentPageIndex + 1
                isUpdatingSlider = false
            }

            val page = pages[currentPageIndex]
            imageLabel.icon = null
            imageLabel.text = "Descargando página ${currentPageIndex + 1} de ${pages.size}..."

            scope.launch(Dispatchers.IO) {
                val img = fetchImageFromNetwork(page.imageUrl)
                if (img != null) {
                    val targetWidth = 860
                    val scaledHeight = (img.getHeight(null) * (targetWidth.toDouble() / img.getWidth(null))).toInt()
                    val scaled = img.getScaledInstance(targetWidth, scaledHeight, Image.SCALE_SMOOTH)
                    withContext(Dispatchers.Swing) {
                        imageLabel.icon = ImageIcon(scaled)
                        imageLabel.text = ""
                        pageScrollPane.verticalScrollBar.value = 0
                    }
                } else {
                    withContext(Dispatchers.Swing) {
                        imageLabel.text = "Error al descargar página ${currentPageIndex + 1}"
                    }
                }
            }
        }

        var webtoonLoaded = false
        fun toggleWebtoonMode() {
            isWebtoonMode = !isWebtoonMode
            if (isWebtoonMode) {
                cardLayout.show(centerCardPanel, "WEBTOON")
                webtoonToggleBtn.text = "📄 Modo Página (W)"
                webtoonToggleBtn.background = Theme.ACCENT_CYAN
                pageSlider.isVisible = false
                prevBtn.isVisible = false
                nextBtn.isVisible = false
                pageStatus.text = "Modo Cascada Webtoon (${pages.size} páginas continuas)"

                if (!webtoonLoaded) {
                    webtoonLoaded = true
                    for ((idx, page) in pages.withIndex()) {
                        val pageLbl = JLabel("Cargando página ${idx + 1} de ${pages.size}...", SwingConstants.CENTER)
                        pageLbl.alignmentX = Component.CENTER_ALIGNMENT
                        pageLbl.foreground = Color.GRAY
                        pageLbl.background = Color.BLACK
                        pageLbl.isOpaque = true
                        pageLbl.preferredSize = Dimension(900, 350)
                        webtoonContainer.add(pageLbl)

                        scope.launch(Dispatchers.IO) {
                            val img = fetchImageFromNetwork(page.imageUrl)
                            if (img != null) {
                                val targetWidth = 900
                                val scaledHeight = (img.getHeight(null) * (targetWidth.toDouble() / img.getWidth(null))).toInt()
                                val scaled = img.getScaledInstance(targetWidth, scaledHeight, Image.SCALE_SMOOTH)
                                withContext(Dispatchers.Swing) {
                                    pageLbl.preferredSize = null
                                    pageLbl.icon = ImageIcon(scaled)
                                    pageLbl.text = ""
                                    pageLbl.revalidate()
                                    pageLbl.repaint()
                                }
                            } else {
                                withContext(Dispatchers.Swing) {
                                    pageLbl.text = "Error cargando página ${idx + 1}"
                                }
                            }
                        }
                    }
                    webtoonContainer.revalidate()
                    webtoonContainer.repaint()
                }
            } else {
                cardLayout.show(centerCardPanel, "PAGE")
                webtoonToggleBtn.text = "📜 Cascada Webtoon (W)"
                webtoonToggleBtn.background = Theme.ACCENT_PURPLE
                pageSlider.isVisible = true
                prevBtn.isVisible = true
                nextBtn.isVisible = true
                showPage(currentPageIndex)
            }
        }

        pageSlider.addChangeListener {
            if (!pageSlider.valueIsAdjusting && !isUpdatingSlider) {
                showPage(pageSlider.value - 1)
            }
        }

        prevBtn.addActionListener { if (currentPageIndex > 0) showPage(currentPageIndex - 1) }
        nextBtn.addActionListener { if (currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }
        webtoonToggleBtn.addActionListener { toggleWebtoonMode() }

        // Atajos de teclado estilo Aniyomi Desktop
        readerDialog.rootPane.registerKeyboardAction({ if (!isWebtoonMode && currentPageIndex > 0) showPage(currentPageIndex - 1) }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ if (!isWebtoonMode && currentPageIndex > 0) showPage(currentPageIndex - 1) }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ if (!isWebtoonMode && currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ if (!isWebtoonMode && currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ if (!isWebtoonMode && currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ toggleWebtoonMode() }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        readerDialog.rootPane.registerKeyboardAction({ readerDialog.dispose() }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)

        readerDialog.contentPane.add(topBar, BorderLayout.NORTH)
        readerDialog.contentPane.add(centerCardPanel, BorderLayout.CENTER)
        readerDialog.contentPane.add(bottomBar, BorderLayout.SOUTH)

        showPage(0)
        readerDialog.isVisible = true
    }

    // ==========================================
    // HELPERS DE ESTILO Y COMPONENTES
    // ==========================================
    private fun createStyledTextField(placeholder: String): JTextField {
        val field = JTextField(18)
        field.font = Font("Segoe UI", Font.PLAIN, 13)
        field.background = Theme.BG_INPUT
        field.foreground = Theme.TEXT_WHITE
        field.caretColor = Color.WHITE
        field.border = BorderFactory.createCompoundBorder(
            RoundedBorder(Theme.BORDER_COLOR, 10, 1),
            EmptyBorder(6, 12, 6, 12)
        )
        return field
    }

    private fun createStyledComboBox(items: Array<String>): JComboBox<String> {
        val combo = JComboBox(items)
        combo.font = Font("Segoe UI", Font.PLAIN, 12)
        combo.background = Theme.BG_INPUT
        combo.foreground = Theme.TEXT_WHITE
        combo.border = RoundedBorder(Theme.BORDER_COLOR, 10, 1)
        return combo
    }

    private fun createActionButton(text: String, bg: Color): JButton {
        val btn = JButton(text)
        btn.font = Font("Segoe UI", Font.BOLD, 12)
        btn.isFocusPainted = false
        btn.isContentAreaFilled = false
        btn.isOpaque = true
        btn.background = bg
        btn.foreground = Color.WHITE
        btn.border = RoundedBorder(bg, 10, 1)
        btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        btn.margin = Insets(6, 14, 6, 14)
        return btn
    }

    private fun createLoadingNotice(msg: String): JLabel {
        val lbl = JLabel(msg, SwingConstants.CENTER)
        lbl.font = Font("Segoe UI", Font.BOLD, 15)
        lbl.foreground = Theme.ACCENT_PURPLE
        return lbl
    }

    private fun styleScrollBar(scrollPane: JScrollPane) {
        scrollPane.verticalScrollBar.background = Theme.BG_MAIN
        scrollPane.horizontalScrollBar.background = Theme.BG_MAIN
    }
}

// ==========================================
// TARJETA DE CÓMIC / ANIME CON DEGRADADO Y BADGES
// ==========================================
class ModernMediaCard(
    private val media: MediaItem,
    private val isAnime: Boolean,
    private val onClick: () -> Unit
) : JPanel(BorderLayout()) {

    private var isHovered = false

    init {
        preferredSize = Dimension(190, 280)
        background = Theme.BG_SURFACE
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = RoundedBorder(Theme.BORDER_COLOR, 12, 1)

        val posterLayer = JLayeredPane()
        posterLayer.preferredSize = Dimension(190, 280)

        val imgLabel = JLabel("Cargando...", SwingConstants.CENTER)
        imgLabel.setBounds(0, 0, 190, 280)
        imgLabel.background = Theme.BG_SURFACE
        imgLabel.foreground = Theme.TEXT_MUTED
        imgLabel.isOpaque = true

        // Badge superior tipo pill: ANIME o MANHWA
        val badge = JLabel(if (isAnime) " ANIME " else if (media.title.contains("Manhwa")) " MANHWA " else " MANGA ")
        badge.font = Font("Segoe UI", Font.BOLD, 10)
        badge.foreground = Color.WHITE
        badge.background = if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE
        badge.isOpaque = true
        badge.setBounds(10, 10, 65, 20)
        badge.border = RoundedBorder(badge.background, 6, 1)
        badge.horizontalAlignment = SwingConstants.CENTER

        // Overlay inferior con degradado para el título
        val titleOverlay = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val gradient = GradientPaint(0f, 0f, Color(0, 0, 0, 0), 0f, height.toFloat(), Color(10, 11, 18, 240))
                g2.paint = gradient
                g2.fillRect(0, 0, width, height)
            }
        }
        titleOverlay.layout = BorderLayout()
        titleOverlay.isOpaque = false
        titleOverlay.setBounds(0, 180, 190, 100)
        titleOverlay.border = EmptyBorder(10, 10, 8, 10)

        val cleanTitle = media.title.replace(Regex("""\[.*?\]"""), "").trim()
        val titleLbl = JLabel("<html><b>$cleanTitle</b></html>")
        titleLbl.font = Font("Segoe UI", Font.BOLD, 12)
        titleLbl.foreground = Color.WHITE

        val genresLbl = JLabel(media.genres.take(2).joinToString(" • "))
        genresLbl.font = Font("Segoe UI", Font.PLAIN, 10)
        genresLbl.foreground = Theme.TEXT_MUTED

        val textCol = JPanel()
        textCol.layout = BoxLayout(textCol, BoxLayout.Y_AXIS)
        textCol.isOpaque = false
        textCol.add(titleLbl)
        textCol.add(Box.createVerticalStrut(2))
        textCol.add(genresLbl)

        titleOverlay.add(textCol, BorderLayout.SOUTH)

        posterLayer.add(imgLabel, Integer.valueOf(1))
        posterLayer.add(badge, Integer.valueOf(2))
        posterLayer.add(titleOverlay, Integer.valueOf(3))

        add(posterLayer, BorderLayout.CENTER)

        // Carga asíncrona de carátula en alta definición
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val img = ImageIO.read(URL(media.coverUrl))
                if (img != null) {
                    val scaled = img.getScaledInstance(190, 280, Image.SCALE_SMOOTH)
                    withContext(Dispatchers.Swing) {
                        imgLabel.icon = ImageIcon(scaled)
                        imgLabel.text = ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Swing) {
                    imgLabel.text = cleanTitle.take(15)
                }
            }
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) { onClick() }
            override fun mouseEntered(e: MouseEvent?) {
                isHovered = true
                border = RoundedBorder(if (isAnime) Theme.ACCENT_PURPLE else Theme.ACCENT_ROSE, 12, 2)
                repaint()
            }
            override fun mouseExited(e: MouseEvent?) {
                isHovered = false
                border = RoundedBorder(Theme.BORDER_COLOR, 12, 1)
                repaint()
            }
        })
    }
}

// Borde redondeado suave para componentes modernos
class RoundedBorder(
    private val color: Color,
    private val radius: Int,
    private val thickness: Int = 1
) : AbstractBorder() {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.stroke = BasicStroke(thickness.toFloat())
        g2.draw(RoundRectangle2D.Float(
            x.toFloat() + thickness / 2f,
            y.toFloat() + thickness / 2f,
            (width - thickness).toFloat(),
            (height - thickness).toFloat(),
            radius.toFloat(),
            radius.toFloat()
        ))
    }
    override fun getBorderInsets(c: Component): Insets = Insets(thickness + 2, thickness + 4, thickness + 2, thickness + 4)
}
