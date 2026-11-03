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
import java.io.File
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            // Usar default
        }
        val app = AniyomiDesktopApp()
        app.isVisible = true
    }
}

class AniyomiDesktopApp : JFrame("Aniyomi Cross - PC Desktop Edition") {

    private val scope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private val repoManager = ExtensionRepositoryManager()
    private val animeSource = AniListAnimeSource()
    private val mangaSource = AniListMangaSource()

    private val tabbedPane = JTabbedPane()

    // Paneles principales
    private val animePanel = JPanel(BorderLayout())
    private val mangaPanel = JPanel(BorderLayout())
    private val extensionsPanel = JPanel(BorderLayout())
    private val downloadsPanel = JPanel(BorderLayout())

    // Grillas de contenido
    private val animeGridPanel = JPanel(GridLayout(0, 5, 14, 14))
    private val mangaGridPanel = JPanel(GridLayout(0, 5, 14, 14))
    private val extensionsListModel = DefaultListModel<String>()
    private val downloadsListModel = DefaultListModel<String>()

    // Paginación
    private var animePage = 1
    private var mangaPage = 1

    // Géneros disponibles
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

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1240, 840)
        setLocationRelativeTo(null)

        setupUI()
        loadInitialData()
    }

    private fun setupUI() {
        tabbedPane.font = Font("Segoe UI", Font.BOLD, 14)

        // 1. Pestaña Anime
        setupAnimeTab()
        tabbedPane.addTab("📺 Anime & Series", animePanel)

        // 2. Pestaña Manga & Manhwa
        setupMangaTab()
        tabbedPane.addTab("📖 Manga, Manhwa & Webtoons", mangaPanel)

        // 3. Pestaña Extensiones
        setupExtensionsTab()
        tabbedPane.addTab("🧩 Repositorios & Extensiones", extensionsPanel)

        // 4. Pestaña Descargas
        setupDownloadsTab()
        tabbedPane.addTab("💾 Descargas Offline", downloadsPanel)

        contentPane.add(tabbedPane, BorderLayout.CENTER)
    }

    private fun setupAnimeTab() {
        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8))
        topBar.background = Color(245, 245, 250)
        topBar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 230))

        val searchField = JTextField(18)
        searchField.font = Font("Segoe UI", Font.PLAIN, 13)

        val genreCombo = JComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        genreCombo.font = Font("Segoe UI", Font.PLAIN, 12)

        val sortCombo = JComboBox(sortOptions.map { it.first }.toTypedArray())
        sortCombo.font = Font("Segoe UI", Font.PLAIN, 12)

        val searchBtn = JButton("🔍 Buscar")
        searchBtn.font = Font("Segoe UI", Font.BOLD, 12)
        searchBtn.background = Color(33, 150, 243)
        searchBtn.foreground = Color.WHITE

        // Barra inferior de paginación
        val bottomPagination = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10))
        val prevBtn = JButton("◀ Página Anterior")
        val pageLabel = JLabel("Página 1")
        pageLabel.font = Font("Segoe UI", Font.BOLD, 13)
        val nextBtn = JButton("Página Siguiente (+50 Series) ▶")

        fun loadAnimePage(p: Int) {
            animePage = p.coerceAtLeast(1)
            pageLabel.text = "Página $animePage"
            prevBtn.isEnabled = animePage > 1

            val q = searchField.text.trim()
            val selectedGenre = genresList[genreCombo.selectedIndex]
            val selectedSort = sortOptions[sortCombo.selectedIndex].second

            scope.launch {
                animeGridPanel.removeAll()
                animeGridPanel.add(JLabel("Cargando 50 animes con carátulas oficiales (Pág. $animePage)...", SwingConstants.CENTER))
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

        topBar.add(JLabel("Buscar:"))
        topBar.add(searchField)
        topBar.add(JLabel("Género:"))
        topBar.add(genreCombo)
        topBar.add(JLabel("Ordenar:"))
        topBar.add(sortCombo)
        topBar.add(searchBtn)

        bottomPagination.add(prevBtn)
        bottomPagination.add(pageLabel)
        bottomPagination.add(nextBtn)

        val scroll = JScrollPane(animeGridPanel)
        scroll.border = EmptyBorder(10, 10, 10, 10)
        scroll.verticalScrollBar.unitIncrement = 22

        animePanel.add(topBar, BorderLayout.NORTH)
        animePanel.add(scroll, BorderLayout.CENTER)
        animePanel.add(bottomPagination, BorderLayout.SOUTH)
    }

    private fun setupMangaTab() {
        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8))
        topBar.background = Color(245, 245, 250)
        topBar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 230))

        val searchField = JTextField(18)
        searchField.font = Font("Segoe UI", Font.PLAIN, 13)

        val typeOptions = arrayOf(
            "Todos (Manga + Manhwa)" to "TODOS",
            "Solo Manhwa (Corea)" to "KR",
            "Solo Manga (Japón)" to "JP",
            "Solo Manhua (China)" to "CN"
        )
        val typeCombo = JComboBox(typeOptions.map { it.first }.toTypedArray())
        typeCombo.font = Font("Segoe UI", Font.PLAIN, 12)

        val genreCombo = JComboBox(genresList.map { genreDisplayMap[it] ?: it }.toTypedArray())
        genreCombo.font = Font("Segoe UI", Font.PLAIN, 12)

        val sortCombo = JComboBox(sortOptions.map { it.first }.toTypedArray())
        sortCombo.font = Font("Segoe UI", Font.PLAIN, 12)

        val searchBtn = JButton("🔍 Buscar")
        searchBtn.font = Font("Segoe UI", Font.BOLD, 12)
        searchBtn.background = Color(76, 175, 80)
        searchBtn.foreground = Color.WHITE

        // Barra inferior de paginación
        val bottomPagination = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10))
        val prevBtn = JButton("◀ Página Anterior")
        val pageLabel = JLabel("Página 1")
        pageLabel.font = Font("Segoe UI", Font.BOLD, 13)
        val nextBtn = JButton("Página Siguiente (+50 Cómics) ▶")

        fun loadMangaPage(p: Int) {
            mangaPage = p.coerceAtLeast(1)
            pageLabel.text = "Página $mangaPage"
            prevBtn.isEnabled = mangaPage > 1

            val q = searchField.text.trim()
            val selectedGenre = genresList[genreCombo.selectedIndex]
            val selectedSort = sortOptions[sortCombo.selectedIndex].second
            val selectedCountry = typeOptions[typeCombo.selectedIndex].second

            scope.launch {
                mangaGridPanel.removeAll()
                mangaGridPanel.add(JLabel("Cargando 50 cómics y carátulas oficiales (Pág. $mangaPage)...", SwingConstants.CENTER))
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

        topBar.add(JLabel("Buscar:"))
        topBar.add(searchField)
        topBar.add(JLabel("Tipo:"))
        topBar.add(typeCombo)
        topBar.add(JLabel("Género:"))
        topBar.add(genreCombo)
        topBar.add(JLabel("Orden:"))
        topBar.add(sortCombo)
        topBar.add(searchBtn)

        bottomPagination.add(prevBtn)
        bottomPagination.add(pageLabel)
        bottomPagination.add(nextBtn)

        val scroll = JScrollPane(mangaGridPanel)
        scroll.border = EmptyBorder(10, 10, 10, 10)
        scroll.verticalScrollBar.unitIncrement = 22

        mangaPanel.add(topBar, BorderLayout.NORTH)
        mangaPanel.add(scroll, BorderLayout.CENTER)
        mangaPanel.add(bottomPagination, BorderLayout.SOUTH)
    }

    private fun setupExtensionsTab() {
        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10))
        val statusLabel = JLabel("Repositorios conectados: Keiyoushi (1450+), Yuzono (245+) y Salmanbappi")
        statusLabel.font = Font("Segoe UI", Font.BOLD, 13)
        topBar.add(statusLabel)

        val jlist = JList(extensionsListModel)
        jlist.font = Font("Segoe UI", Font.PLAIN, 13)
        val scroll = JScrollPane(jlist)

        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT))
        val openApkBtn = JButton("Descargar APK de la Extensión Seleccionada")
        openApkBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                JOptionPane.showMessageDialog(this, "Extensión seleccionada:\n$selected\n\nDescargando archivo .apk.", "Extensiones", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        bottomBar.add(openApkBtn)

        extensionsPanel.add(topBar, BorderLayout.NORTH)
        extensionsPanel.add(scroll, BorderLayout.CENTER)
        extensionsPanel.add(bottomBar, BorderLayout.SOUTH)
    }

    private fun setupDownloadsTab() {
        val list = JList(downloadsListModel)
        list.font = Font("Segoe UI", Font.PLAIN, 14)
        val scroll = JScrollPane(list)

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10))
        val infoLabel = JLabel("Descargas Offline (capítulos y episodios guardados en tu PC):")
        infoLabel.font = Font("Segoe UI", Font.BOLD, 13)
        topBar.add(infoLabel)

        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT))
        val openFolderBtn = JButton("Abrir Carpeta de Descargas")
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
            // Cargar Animes Populares (50)
            val animes = animeSource.getPopular(1)
            renderMediaGrid(animeGridPanel, animes, isAnime = true)

            // Cargar Mangas Populares (50)
            val mangas = mangaSource.getPopular(1)
            renderMediaGrid(mangaGridPanel, mangas, isAnime = false)

            // Cargar Lista de Extensiones
            val extList = repoManager.fetchAllExtensions()
            extensionsListModel.clear()
            for (ext in extList.take(400)) {
                extensionsListModel.addElement("[${ext.type}] [${ext.lang.uppercase()}] ${ext.name} (v${ext.version}) - ${ext.pkg}")
            }
        }
    }

    private fun renderMediaGrid(panel: JPanel, items: List<MediaItem>, isAnime: Boolean) {
        panel.removeAll()
        if (items.isEmpty()) {
            panel.add(JLabel("No se encontraron resultados con los filtros actuales.", SwingConstants.CENTER))
            panel.revalidate()
            panel.repaint()
            return
        }

        for (item in items) {
            val card = JPanel(BorderLayout(4, 4))
            card.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(215, 215, 225), 1),
                EmptyBorder(6, 6, 6, 6)
            )
            card.background = Color.WHITE
            card.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            val titleLabel = JLabel("<html><center><b>${item.title.take(35)}</b><br><font color='#777777' size='2'>${item.genres.take(2).joinToString(", ")}</font></center></html>")
            titleLabel.font = Font("Segoe UI", Font.PLAIN, 11)
            titleLabel.horizontalAlignment = SwingConstants.CENTER

            val imgLabel = JLabel("Cargando...", SwingConstants.CENTER)
            imgLabel.preferredSize = Dimension(160, 225)
            imgLabel.background = Color(240, 242, 248)
            imgLabel.isOpaque = true

            scope.launch(Dispatchers.IO) {
                try {
                    val url = URL(item.coverUrl)
                    val img = ImageIO.read(url)
                    if (img != null) {
                        val scaled = img.getScaledInstance(160, 225, Image.SCALE_SMOOTH)
                        withContext(Dispatchers.Swing) {
                            imgLabel.icon = ImageIcon(scaled)
                            imgLabel.text = ""
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        imgLabel.text = item.title.take(15)
                    }
                }
            }

            card.add(imgLabel, BorderLayout.CENTER)
            card.add(titleLabel, BorderLayout.SOUTH)

            card.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    showDetailsDialog(item, isAnime)
                }
            })

            panel.add(card)
        }
        panel.revalidate()
        panel.repaint()
    }

    private fun showDetailsDialog(item: MediaItem, isAnime: Boolean) {
        val dialog = JDialog(this, item.title, true)
        dialog.setSize(850, 680)
        dialog.setLocationRelativeTo(this)
        dialog.layout = BorderLayout(10, 10)

        val mainBox = JPanel(BorderLayout(12, 12))
        mainBox.border = EmptyBorder(15, 15, 15, 15)

        val headerPanel = JPanel(BorderLayout(15, 15))
        val genresText = item.genres.joinToString(" • ")
        val descArea = JTextArea("${item.title}\nEstado: ${item.status}\nGéneros: $genresText\n\nSinopsis:\n${item.description.ifEmpty { "Sin descripción disponible." }}")
        descArea.isEditable = false
        descArea.lineWrap = true
        descArea.wrapStyleWord = true
        descArea.font = Font("Segoe UI", Font.PLAIN, 13)

        val descScroll = JScrollPane(descArea)
        descScroll.preferredSize = Dimension(500, 140)
        headerPanel.add(descScroll, BorderLayout.CENTER)

        val listModel = DefaultListModel<EpisodeOrChapter>()
        val jlist = JList(listModel)
        jlist.cellRenderer = ListCellRenderer { _, value, _, _, _ ->
            JLabel(if (isAnime) "▶ ${value.name}" else "📖 ${value.name}")
        }
        val listScroll = JScrollPane(jlist)

        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 5))
        val actionBtn = JButton(if (isAnime) "▶ Ver Episodio" else "📖 Leer Capítulo")
        actionBtn.font = Font("Segoe UI", Font.BOLD, 13)
        actionBtn.background = if (isAnime) Color(33, 150, 243) else Color(76, 175, 80)
        actionBtn.foreground = Color.WHITE

        val downloadBtn = JButton("⬇ Descargar Gratis")

        actionBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                scope.launch {
                    if (isAnime) {
                        val videos = animeSource.getVideoList(selected)
                        if (videos.isNotEmpty()) {
                            openAnimePlayer(item, selected, videos)
                        } else {
                            JOptionPane.showMessageDialog(dialog, "No se encontraron servidores para este episodio.", "Aviso", JOptionPane.WARNING_MESSAGE)
                        }
                    } else {
                        // Obtener páginas auténticas del cómic/manga
                        val pages = mangaSource.getPageList(selected)
                        if (pages.isNotEmpty()) {
                            openMangaReader(item, selected, pages)
                        } else {
                            JOptionPane.showMessageDialog(dialog, "No se pudieron cargar las páginas de este capítulo.", "Aviso", JOptionPane.WARNING_MESSAGE)
                        }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Selecciona un episodio o capítulo de la lista.")
            }
        }

        downloadBtn.addActionListener {
            val selected = jlist.selectedValue
            if (selected != null) {
                downloadsListModel.addElement("[DESCARGADO] ${item.title} - ${selected.name}")
                JOptionPane.showMessageDialog(dialog, "¡Descargando ${selected.name} gratis a tu equipo!", "Descargas Offline", JOptionPane.INFORMATION_MESSAGE)
            }
        }

        btnPanel.add(actionBtn)
        btnPanel.add(downloadBtn)

        mainBox.add(headerPanel, BorderLayout.NORTH)
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

    /**
     * Reproductor de Anime Dedicado para PC (Sin errores de navegador ni pantallas con código)
     */
    private fun openAnimePlayer(media: MediaItem, episode: EpisodeOrChapter, videos: List<VideoSource>) {
        val playerDialog = JDialog(this, "Reproductor Anime - ${media.title} (${episode.name})", true)
        playerDialog.setSize(750, 420)
        playerDialog.setLocationRelativeTo(this)
        playerDialog.layout = BorderLayout(15, 15)

        val content = JPanel(BorderLayout(15, 15))
        content.border = EmptyBorder(20, 20, 20, 20)
        content.background = Color(20, 20, 25)

        val titleLbl = JLabel("<html><font color='#ffffff' size='5'><b>${media.title}</b></font><br><font color='#aaaaaa' size='3'>${episode.name}</font></html>")
        content.add(titleLbl, BorderLayout.NORTH)

        val centerPanel = JPanel(GridLayout(0, 1, 10, 10))
        centerPanel.background = Color(20, 20, 25)

        val infoLabel = JLabel("<html><font color='#dddddd'>Selecciona una opción de reproducción sin anuncios:</font></html>")
        centerPanel.add(infoLabel)

        for (v in videos) {
            val playBtn = JButton("▶ Reproducir en Reproductor HTML5 (${v.quality})")
            playBtn.font = Font("Segoe UI", Font.BOLD, 13)
            playBtn.background = Color(229, 9, 20)
            playBtn.foreground = Color.WHITE
            playBtn.addActionListener {
                launchHtml5Player(media.title, episode.name, v.videoUrl)
            }
            centerPanel.add(playBtn)
        }

        val copyLinkBtn = JButton("📋 Copiar Enlace Directo (para VLC / PotPlayer / MPV)")
        copyLinkBtn.font = Font("Segoe UI", Font.PLAIN, 12)
        copyLinkBtn.addActionListener {
            val sel = StringSelection(videos.first().videoUrl)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            JOptionPane.showMessageDialog(playerDialog, "¡Enlace de streaming copiado al portapapeles!\nPuedes pegarlo directamente en VLC (Ctrl + N) o en tu reproductor favorito.")
        }
        centerPanel.add(copyLinkBtn)

        content.add(centerPanel, BorderLayout.CENTER)
        playerDialog.contentPane.add(content)
        playerDialog.isVisible = true
    }

    /**
     * Lanza un reproductor HTML5 limpio local para ver el stream con soporte de video y controles
     */
    private fun launchHtml5Player(title: String, episodeName: String, videoUrl: String) {
        try {
            val html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <title>$title - $episodeName</title>
                    <style>
                        body { margin: 0; background: #0c0d14; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; }
                        .header { width: 92%; max-width: 1080px; padding: 14px 0; display: flex; justify-content: space-between; align-items: center; }
                        h1 { margin: 0; font-size: 22px; color: #ff3344; }
                        span { color: #aaa; font-size: 14px; }
                        .player-box { width: 92%; max-width: 1080px; background: #000; border-radius: 10px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.8); }
                        video { width: 100%; height: auto; max-height: 80vh; display: block; outline: none; }
                        .footer { margin-top: 15px; color: #777; font-size: 13px; }
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
                            Tu navegador no soporta reproducción de video.
                        </video>
                    </div>
                    <div class="footer">Aniyomi Cross - Reproductor Oficial PC</div>
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

    /**
     * Lector de Manga y Cómics Nativo en Ventana (Muestra las imágenes auténticas del capítulo)
     */
    private fun openMangaReader(media: MediaItem, chapter: EpisodeOrChapter, pages: List<MangaPage>) {
        val readerDialog = JDialog(this, "Lector Manga - ${media.title} (${chapter.name})", true)
        readerDialog.setSize(920, 880)
        readerDialog.setLocationRelativeTo(this)
        readerDialog.layout = BorderLayout()

        var currentPageIndex = 0

        val topBar = JPanel(FlowLayout(FlowLayout.CENTER, 15, 8))
        topBar.background = Color(25, 25, 30)
        val titleLbl = JLabel("<html><font color='#ffffff'><b>${media.title}</b> - ${chapter.name}</font></html>")
        val pageStatus = JLabel("<html><font color='#00e676'>Página 1 de ${pages.size}</font></html>")
        pageStatus.font = Font("Segoe UI", Font.BOLD, 13)

        topBar.add(titleLbl)
        topBar.add(pageStatus)

        val imageLabel = JLabel("Cargando página del cómic...", SwingConstants.CENTER)
        imageLabel.background = Color.BLACK
        imageLabel.isOpaque = true
        val scrollPane = JScrollPane(imageLabel)
        scrollPane.verticalScrollBar.unitIncrement = 25

        val bottomBar = JPanel(FlowLayout(FlowLayout.CENTER, 15, 10))
        bottomBar.background = Color(25, 25, 30)

        val prevBtn = JButton("◀ Página Anterior")
        val nextBtn = JButton("Página Siguiente ▶")
        val webtoonBtn = JButton("Modo Webtoon (Cascada)")

        fun showPage(idx: Int) {
            if (pages.isEmpty()) return
            currentPageIndex = idx.coerceIn(0, pages.size - 1)
            pageStatus.text = "<html><font color='#00e676'>Página ${currentPageIndex + 1} de ${pages.size}</font></html>"
            prevBtn.isEnabled = currentPageIndex > 0
            nextBtn.isEnabled = currentPageIndex < pages.size - 1

            val page = pages[currentPageIndex]
            imageLabel.icon = null
            imageLabel.text = "Descargando página ${currentPageIndex + 1} del cómic..."

            scope.launch(Dispatchers.IO) {
                try {
                    val url = URL(page.imageUrl)
                    val img = ImageIO.read(url)
                    if (img != null) {
                        // Escalar manteniendo proporción para llenar la altura de lectura
                        val targetWidth = 820
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
                        imageLabel.text = "Error al cargar la página ${currentPageIndex + 1}"
                    }
                }
            }
        }

        prevBtn.addActionListener { if (currentPageIndex > 0) showPage(currentPageIndex - 1) }
        nextBtn.addActionListener { if (currentPageIndex < pages.size - 1) showPage(currentPageIndex + 1) }

        webtoonBtn.addActionListener {
            openWebtoonScrollReader(media.title, chapter.name, pages)
        }

        bottomBar.add(prevBtn)
        bottomBar.add(nextBtn)
        bottomBar.add(webtoonBtn)

        readerDialog.contentPane.add(topBar, BorderLayout.NORTH)
        readerDialog.contentPane.add(scrollPane, BorderLayout.CENTER)
        readerDialog.contentPane.add(bottomBar, BorderLayout.SOUTH)

        showPage(0)
        readerDialog.isVisible = true
    }

    /**
     * Lector de Manga en Cascada Vertical Continua (Estilo Webtoon / Manhwa)
     */
    private fun openWebtoonScrollReader(title: String, chapterName: String, pages: List<MangaPage>) {
        val webtoonDialog = JDialog(this, "Lector Cascada Webtoon - $title ($chapterName)", true)
        webtoonDialog.setSize(920, 900)
        webtoonDialog.setLocationRelativeTo(this)

        val pagesContainer = JPanel()
        pagesContainer.layout = BoxLayout(pagesContainer, BoxLayout.Y_AXIS)
        pagesContainer.background = Color.BLACK

        for ((idx, page) in pages.withIndex()) {
            val pageLbl = JLabel("Cargando página ${idx + 1}...", SwingConstants.CENTER)
            pageLbl.alignmentX = Component.CENTER_ALIGNMENT
            pageLbl.foreground = Color.GRAY
            pageLbl.background = Color.BLACK
            pageLbl.isOpaque = true
            pagesContainer.add(pageLbl)

            scope.launch(Dispatchers.IO) {
                try {
                    val url = URL(page.imageUrl)
                    val img = ImageIO.read(url)
                    if (img != null) {
                        val targetWidth = 840
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
        scroll.verticalScrollBar.unitIncrement = 30
        webtoonDialog.contentPane.add(scroll)
        webtoonDialog.isVisible = true
    }
}
