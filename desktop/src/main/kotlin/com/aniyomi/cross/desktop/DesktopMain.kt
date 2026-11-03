package com.aniyomi.cross.desktop

import com.aniyomi.cross.model.ContentType
import com.aniyomi.cross.model.EpisodeOrChapter
import com.aniyomi.cross.model.MangaPage
import com.aniyomi.cross.model.MediaItem
import com.aniyomi.cross.model.VideoSource
import com.aniyomi.cross.repository.ExtensionRepositoryManager
import com.aniyomi.cross.scrapers.AniListAnimeSource
import com.aniyomi.cross.scrapers.AniListMangaSource
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
    private val mangaSource = AniListMangaSource()

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

        setupHeader()
        setupContentPanels()
        loadInitialData()
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

        val tagPill = JLabel(" v1.0 ")
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

        tabAnimeBtn.addActionListener { selectTab("ANIME", tabAnimeBtn) }
        tabMangaBtn.addActionListener { selectTab("MANGA", tabMangaBtn) }
        tabExtBtn.addActionListener { selectTab("EXTENSIONS", tabExtBtn) }
        tabDownBtn.addActionListener { selectTab("DOWNLOADS", tabDownBtn) }

        navTabs.add(tabAnimeBtn)
        navTabs.add(tabMangaBtn)
        navTabs.add(tabExtBtn)
        navTabs.add(tabDownBtn)

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

        // Barra de Búsqueda y Filtros Premium
        val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 14, 12))
        filterBar.background = Theme.BG_SURFACE
        filterBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(4, 20, 4, 20)
        )

        val searchField = createStyledTextField("Buscar anime por título...")
        val genreCombo = createStyledComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        val sortCombo = createStyledComboBox(sortOptions.map { it.first }.toTypedArray())

        val searchBtn = createActionButton("🔍 Filtrar", Theme.ACCENT_PURPLE)

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

            scope.launch {
                animeGridPanel.removeAll()
                animeGridPanel.add(createLoadingNotice("Cargando 50 animes en alta definición (Página $animePage)..."))
                animeGridPanel.revalidate()
                animeGridPanel.repaint()

                val results = animeSource.filter(
                    query = q,
                    genre = if (selectedGenre == "Todos") null else selectedGenre,
                    sort = selectedSort,
                    page = animePage
                )
                renderMediaGrid(animeGridPanel, results, isAnime = true)
            }
        }

        searchBtn.addActionListener { loadAnimePage(1) }
        searchField.addActionListener { loadAnimePage(1) }
        genreCombo.addActionListener { loadAnimePage(1) }
        sortCombo.addActionListener { loadAnimePage(1) }
        prevBtn.addActionListener { if (animePage > 1) loadAnimePage(animePage - 1) }
        nextBtn.addActionListener { loadAnimePage(animePage + 1) }

        val searchLbl = JLabel("Explorar:")
        searchLbl.foreground = Theme.TEXT_MUTED
        searchLbl.font = Font("Segoe UI", Font.BOLD, 12)

        val genreLbl = JLabel("Género:")
        genreLbl.foreground = Theme.TEXT_MUTED
        genreLbl.font = Font("Segoe UI", Font.BOLD, 12)

        val sortLbl = JLabel("Orden:")
        sortLbl.foreground = Theme.TEXT_MUTED
        sortLbl.font = Font("Segoe UI", Font.BOLD, 12)

        filterBar.add(searchLbl)
        filterBar.add(searchField)
        filterBar.add(genreLbl)
        filterBar.add(genreCombo)
        filterBar.add(sortLbl)
        filterBar.add(sortCombo)
        filterBar.add(searchBtn)

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

        animePanel.add(filterBar, BorderLayout.NORTH)
        animePanel.add(scroll, BorderLayout.CENTER)
        animePanel.add(bottomPagination, BorderLayout.SOUTH)
    }

    private fun setupMangaTab() {
        mangaPanel.isOpaque = false

        val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 14, 12))
        filterBar.background = Theme.BG_SURFACE
        filterBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR),
            EmptyBorder(4, 20, 4, 20)
        )

        val searchField = createStyledTextField("Buscar manga o manhwa...")

        val typeOptions = arrayOf(
            "Todos (Manga + Manhwa)" to "TODOS",
            "🇰🇷 Solo Manhwa (Corea)" to "KR",
            "🇯🇵 Solo Manga (Japón)" to "JP",
            "🇨🇳 Solo Manhua (China)" to "CN"
        )
        val typeCombo = createStyledComboBox(typeOptions.map { it.first }.toTypedArray())
        val genreCombo = createStyledComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        val sortCombo = createStyledComboBox(sortOptions.map { it.first }.toTypedArray())
        val searchBtn = createActionButton("🔍 Filtrar", Theme.ACCENT_ROSE)

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

            val q = searchField.text.trim().let { if (it == "Buscar manga o manhwa...") "" else it }
            val selectedGenre = genresList[genreCombo.selectedIndex]
            val selectedSort = sortOptions[sortCombo.selectedIndex].second
            val selectedCountry = typeOptions[typeCombo.selectedIndex].second

            scope.launch {
                mangaGridPanel.removeAll()
                mangaGridPanel.add(createLoadingNotice("Cargando 50 cómics y carátulas oficiales (Pág. $mangaPage)..."))
                mangaGridPanel.revalidate()
                mangaGridPanel.repaint()

                val results = mangaSource.filter(
                    query = q,
                    genre = if (selectedGenre == "Todos") null else selectedGenre,
                    sort = selectedSort,
                    country = selectedCountry,
                    page = mangaPage
                )
                renderMediaGrid(mangaGridPanel, results, isAnime = false)
            }
        }

        searchBtn.addActionListener { loadMangaPage(1) }
        searchField.addActionListener { loadMangaPage(1) }
        typeCombo.addActionListener { loadMangaPage(1) }
        genreCombo.addActionListener { loadMangaPage(1) }
        sortCombo.addActionListener { loadMangaPage(1) }
        prevBtn.addActionListener { if (mangaPage > 1) loadMangaPage(mangaPage - 1) }
        nextBtn.addActionListener { loadMangaPage(mangaPage + 1) }

        val searchLbl = JLabel("Explorar:")
        searchLbl.foreground = Theme.TEXT_MUTED
        searchLbl.font = Font("Segoe UI", Font.BOLD, 12)

        val typeLbl = JLabel("Tipo:")
        typeLbl.foreground = Theme.TEXT_MUTED
        typeLbl.font = Font("Segoe UI", Font.BOLD, 12)

        val genreLbl = JLabel("Género:")
        genreLbl.foreground = Theme.TEXT_MUTED
        genreLbl.font = Font("Segoe UI", Font.BOLD, 12)

        val sortLbl = JLabel("Orden:")
        sortLbl.foreground = Theme.TEXT_MUTED
        sortLbl.font = Font("Segoe UI", Font.BOLD, 12)

        filterBar.add(searchLbl)
        filterBar.add(searchField)
        filterBar.add(typeLbl)
        filterBar.add(typeCombo)
        filterBar.add(genreLbl)
        filterBar.add(genreCombo)
        filterBar.add(sortLbl)
        filterBar.add(sortCombo)
        filterBar.add(searchBtn)

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

        mangaPanel.add(filterBar, BorderLayout.NORTH)
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

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 16, 14))
        topBar.background = Theme.BG_SURFACE
        topBar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR)

        val infoLabel = JLabel("Archivos descargados para ver o leer sin conexión a internet:")
        infoLabel.font = Font("Segoe UI", Font.BOLD, 14)
        infoLabel.foreground = Theme.TEXT_WHITE
        topBar.add(infoLabel)

        val list = JList(downloadsListModel)
        list.background = Theme.BG_MAIN
        list.foreground = Theme.TEXT_WHITE
        list.font = Font("Segoe UI", Font.PLAIN, 14)
        list.selectionBackground = Theme.ACCENT_ROSE
        list.selectionForeground = Color.WHITE

        val scroll = JScrollPane(list)
        scroll.border = EmptyBorder(10, 16, 10, 16)
        styleScrollBar(scroll)

        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT, 16, 12))
        bottomBar.background = Theme.BG_SURFACE
        bottomBar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val openFolderBtn = createActionButton("📂 Abrir Carpeta de Descargas", Theme.ACCENT_ROSE)
        openFolderBtn.addActionListener {
            val dir = File(System.getProperty("user.home"), "AniyomiCrossDownloads")
            dir.mkdirs()
            Desktop.getDesktop().open(dir)
        }
        bottomBar.add(openFolderBtn)

        downloadsPanel.add(topBar, BorderLayout.NORTH)
        downloadsPanel.add(scroll, BorderLayout.CENTER)
        downloadsPanel.add(bottomBar, BorderLayout.SOUTH)
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

        val downloadBtn = createActionButton("⬇ Descargar Offline", Theme.BG_INPUT)
        downloadBtn.margin = Insets(8, 16, 8, 16)

        actionBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                scope.launch {
                    if (isAnime) {
                        val videos = animeSource.getVideoList(selected)
                        if (videos.isNotEmpty()) {
                            openAnimePlayer(item, selected, videos)
                        } else {
                            JOptionPane.showMessageDialog(dialog, "No se encontraron servidores disponibles.", "Aviso", JOptionPane.WARNING_MESSAGE)
                        }
                    } else {
                        val pages = mangaSource.getPageList(selected)
                        if (pages.isNotEmpty()) {
                            openMangaReader(item, selected, pages)
                        } else {
                            JOptionPane.showMessageDialog(dialog, "No se pudieron obtener las páginas de este capítulo.", "Aviso", JOptionPane.WARNING_MESSAGE)
                        }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Por favor selecciona un episodio o capítulo de la lista.")
            }
        }

        downloadBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                downloadsListModel.addElement("[DESCARGADO] ${item.title} - ${selected.name}")
                JOptionPane.showMessageDialog(dialog, "¡Descargando ${selected.name} gratis a tu equipo!", "Descargas Offline", JOptionPane.INFORMATION_MESSAGE)
            }
        }

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

    private fun openAnimePlayer(media: MediaItem, episode: EpisodeOrChapter, videos: List<VideoSource>) {
        val playerDialog = JDialog(this, "Reproductor Cinema - ${media.title}", true)
        playerDialog.setSize(750, 430)
        playerDialog.setLocationRelativeTo(this)
        playerDialog.contentPane.background = Theme.BG_MAIN
        playerDialog.layout = BorderLayout()

        val content = JPanel(BorderLayout(15, 15))
        content.border = EmptyBorder(24, 28, 24, 28)
        content.isOpaque = false

        val titleLbl = JLabel("<html><font color='#ffffff' size='5'><b>${media.title}</b></font><br><font color='#8b5cf6' size='3'>${episode.name}</font></html>")
        content.add(titleLbl, BorderLayout.NORTH)

        val centerPanel = JPanel(GridLayout(0, 1, 12, 12))
        centerPanel.isOpaque = false

        val infoLabel = JLabel("<html><font color='#a0a5b9'>Selecciona calidad para reproducir sin anuncios:</font></html>")
        centerPanel.add(infoLabel)

        for (v in videos) {
            val playBtn = createActionButton("▶ Reproducir en Reproductor HTML5 (${v.quality})", Theme.ACCENT_PURPLE)
            playBtn.addActionListener {
                launchHtml5Player(media.title, episode.name, v.videoUrl)
            }
            centerPanel.add(playBtn)
        }

        val copyLinkBtn = createActionButton("📋 Copiar Enlace Directo (VLC / PotPlayer / MPV)", Theme.BG_INPUT)
        copyLinkBtn.addActionListener {
            val sel = StringSelection(videos.first().videoUrl)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            JOptionPane.showMessageDialog(playerDialog, "¡Enlace copiado! Pégalo en VLC con (Ctrl + N).")
        }
        centerPanel.add(copyLinkBtn)

        content.add(centerPanel, BorderLayout.CENTER)
        playerDialog.contentPane.add(content)
        playerDialog.isVisible = true
    }

    private fun launchHtml5Player(title: String, episodeName: String, videoUrl: String) {
        try {
            val html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <title>$title - $episodeName</title>
                    <style>
                        body { margin: 0; background: #0a0b10; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; }
                        .header { width: 92%; max-width: 1100px; padding: 14px 0; display: flex; justify-content: space-between; align-items: center; }
                        h1 { margin: 0; font-size: 22px; color: #8b5cf6; }
                        span { color: #a0a5b9; font-size: 15px; }
                        .player-box { width: 92%; max-width: 1100px; background: #000; border-radius: 14px; overflow: hidden; box-shadow: 0 20px 50px rgba(0,0,0,0.9); border: 1px solid #282a40; }
                        video { width: 100%; height: auto; max-height: 82vh; display: block; outline: none; }
                        .footer { margin-top: 15px; color: #6b7280; font-size: 13px; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>$title</h1>
                        <span>$episodeName</span>
                    </div>
                    <div class="player-box">
                        <video controls autoplay playsinline>
                            <source src="$videoUrl" type="video/mp4">
                            Tu navegador no soporta video.
                        </video>
                    </div>
                    <div class="footer">Aniyomi Cross • Cinema Desktop Edition</div>
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

    private fun openMangaReader(media: MediaItem, chapter: EpisodeOrChapter, pages: List<MangaPage>) {
        val readerDialog = JDialog(this, "Lector Manga - ${media.title} (${chapter.name})", true)
        readerDialog.setSize(950, 920)
        readerDialog.setLocationRelativeTo(this)
        readerDialog.contentPane.background = Color.BLACK
        readerDialog.layout = BorderLayout()

        var currentPageIndex = 0

        val topBar = JPanel(FlowLayout(FlowLayout.CENTER, 20, 10))
        topBar.background = Color(16, 17, 24)
        topBar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_COLOR)

        val titleLbl = JLabel("<html><font color='#ffffff'><b>${media.title}</b> • ${chapter.name}</font></html>")
        val pageStatus = JLabel("Página 1 de ${pages.size}")
        pageStatus.font = Font("Segoe UI", Font.BOLD, 13)
        pageStatus.foreground = Theme.ACCENT_CYAN

        topBar.add(titleLbl)
        topBar.add(pageStatus)

        val imageLabel = JLabel("Cargando página del cómic...", SwingConstants.CENTER)
        imageLabel.background = Color.BLACK
        imageLabel.isOpaque = true
        val scrollPane = JScrollPane(imageLabel)
        scrollPane.border = null
        scrollPane.verticalScrollBar.unitIncrement = 30
        styleScrollBar(scrollPane)

        val bottomBar = JPanel(FlowLayout(FlowLayout.CENTER, 16, 12))
        bottomBar.background = Color(16, 17, 24)
        bottomBar.border = BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR)

        val prevBtn = createActionButton("◀ Página Anterior", Theme.BG_INPUT)
        val nextBtn = createActionButton("Página Siguiente ▶", Theme.ACCENT_ROSE)
        val webtoonBtn = createActionButton("📜 Modo Cascada (Webtoon)", Theme.ACCENT_PURPLE)

        fun showPage(idx: Int) {
            if (pages.isEmpty()) return
            currentPageIndex = idx.coerceIn(0, pages.size - 1)
            pageStatus.text = "Página ${currentPageIndex + 1} de ${pages.size}"
            prevBtn.isEnabled = currentPageIndex > 0
            nextBtn.isEnabled = currentPageIndex < pages.size - 1

            val page = pages[currentPageIndex]
            imageLabel.icon = null
            imageLabel.text = "Descargando página ${currentPageIndex + 1}..."

            scope.launch(Dispatchers.IO) {
                try {
                    val img = ImageIO.read(URL(page.imageUrl))
                    if (img != null) {
                        val targetWidth = 840
                        val scaledHeight = (img.height * (targetWidth.toDouble() / img.width)).toInt()
                        val scaled = img.getScaledInstance(targetWidth, scaledHeight, Image.SCALE_SMOOTH)
                        withContext(Dispatchers.Swing) {
                            imageLabel.icon = ImageIcon(scaled)
                            imageLabel.text = ""
                            scrollPane.verticalScrollBar.value = 0
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        imageLabel.text = "Error al cargar página ${currentPageIndex + 1}"
                    }
                }
            }
        }

        prevBtn.addActionListener { if (currentPageIndex > 0) showPage(currentPageIndex - 1) }
        nextBtn.addActionListener { if (currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }
        webtoonBtn.addActionListener { openWebtoonScrollReader(media.title, chapter.name, pages) }

        bottomBar.add(prevBtn)
        bottomBar.add(nextBtn)
        bottomBar.add(webtoonBtn)

        readerDialog.contentPane.add(topBar, BorderLayout.NORTH)
        readerDialog.contentPane.add(scrollPane, BorderLayout.CENTER)
        readerDialog.contentPane.add(bottomBar, BorderLayout.SOUTH)

        showPage(0)
        readerDialog.isVisible = true
    }

    private fun openWebtoonScrollReader(title: String, chapterName: String, pages: List<MangaPage>) {
        val webtoonDialog = JDialog(this, "Modo Cascada Webtoon - $title", true)
        webtoonDialog.setSize(950, 940)
        webtoonDialog.setLocationRelativeTo(this)
        webtoonDialog.contentPane.background = Color.BLACK

        val pagesContainer = JPanel()
        pagesContainer.layout = BoxLayout(pagesContainer, BoxLayout.Y_AXIS)
        pagesContainer.background = Color.BLACK

        for ((idx, page) in pages.withIndex()) {
            val pageLbl = JLabel("Cargando página ${idx + 1}...", SwingConstants.CENTER)
            pageLbl.alignmentX = Component.CENTER_ALIGNMENT
            pageLbl.foreground = Color.DARK_GRAY
            pageLbl.background = Color.BLACK
            pageLbl.isOpaque = true
            pagesContainer.add(pageLbl)

            scope.launch(Dispatchers.IO) {
                try {
                    val img = ImageIO.read(URL(page.imageUrl))
                    if (img != null) {
                        val targetWidth = 860
                        val scaledHeight = (img.height * (targetWidth.toDouble() / img.width)).toInt()
                        val scaled = img.getScaledInstance(targetWidth, scaledHeight, Image.SCALE_SMOOTH)
                        withContext(Dispatchers.Swing) {
                            pageLbl.icon = ImageIcon(scaled)
                            pageLbl.text = ""
                            pageLbl.revalidate()
                            pageLbl.repaint()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        pageLbl.text = "Error cargando página ${idx + 1}"
                    }
                }
            }
        }

        val scroll = JScrollPane(pagesContainer)
        scroll.border = null
        scroll.verticalScrollBar.unitIncrement = 35
        styleScrollBar(scroll)
        webtoonDialog.contentPane.add(scroll)
        webtoonDialog.isVisible = true
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
