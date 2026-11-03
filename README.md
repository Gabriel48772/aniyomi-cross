# Aniyomi Cross (Multiplataforma PC & Android)

Aplicación multiplataforma inspirada en **Aniyomi**, desarrollada con arquitectura modular en **Kotlin Multiplatform (Android Jetpack Compose + Desktop)** para disfrutar y descargar anime, series, mangas y manhwas de forma completamente gratuita, utilizando los repositorios y extensiones del ecosistema Aniyomi y Tachiyomi / Keiyoushi.

---

## 🚀 Archivos Listos para Usar (`dist/`)

1. **Para Teléfonos Android (APK)**:
   - Archivo: `dist/AniyomiCross-Android.apk`
   - O ejecuta: `install-android.bat` (instala directamente al teléfono conectado por USB con depuración activada).

2. **Para PC (Windows)**:
   - Archivo: `dist/AniyomiCross-PC.jar`
   - O ejecuta directamente con un doble clic: `run-pc.bat`.

---

## 🧩 Repositorios de Extensiones Soportados

La aplicación viene configurada para indexar, descargar y consumir las extensiones de:

### 📺 Extensiones de Anime:
- **Yuzono Anime Repo**: `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json` (y `.pb`)
- **SalmanBappi Anime Repo**: `https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json` (y `.pb`)

### 📖 Extensiones de Manga y Manhwa:
- **Keiyoushi Extensions Repo**: `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json` (más de 1,440 extensiones disponibles en español, inglés y más).
- **Suwayomi Extensions Repo**: `https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json`

---

## 🛠️ Arquitectura del Proyecto

El código está estructurado en 3 módulos limpios:

```text
aniyomi-multiplatform/
├── core/                       # Núcleo compartido (Multiplataforma)
│   ├── model/                  # Modelos de datos (MediaItem, ExtensionMeta, VideoSource, MangaPage)
│   ├── network/                # Cliente OkHttp con DNS Bypass (8.8.8.8/1.1.1.1) y DoH
│   ├── repository/             # Gestor de repositorios (Keiyoushi, Yuzono, Salmanbappi)
│   └── scrapers/               # Fuentes directas (AnimeFLV, MangaDex, JKanime)
├── app/                        # Aplicación Android nativa con Jetpack Compose & Media3 ExoPlayer
│   └── src/main/kotlin/com/aniyomi/cross/
│       ├── MainActivity.kt
│       ├── ui/AniyomiCrossApp.kt # Interfaz Material 3 con 4 pestañas y reproductores
│       └── downloader/         # Gestor de descargas en almacenamiento local
├── desktop/                    # Aplicación de PC (Windows)
│   └── src/main/kotlin/com/aniyomi/cross/desktop/
│       └── DesktopMain.kt      # GUI para PC con buscador, reproductor web y visor
├── dist/                       # Artefactos compilados listos para distribución
│   ├── AniyomiCross-Android.apk
│   └── AniyomiCross-PC.jar
├── run-pc.bat                  # Lanzador directo de 1 clic para PC
├── install-android.bat         # Instalador ADB para Android
└── build-all.bat               # Compilador de ambas versiones con Gradle
```

---

## ✨ Características Principales

1. **Buscador y Catálogo Global**:
   - Pestaña de **Anime**: series en emisión, populares y búsqueda por título.
   - Pestaña de **Manga y Manhwa**: catálogo con lecturas en cascada vertical (Webtoon) y clásico.
2. **Reproductor y Lector Integrados**:
   - Selector de calidad de video (1080p, 720p, etc.) y streaming HLS (`.m3u8`) o MP4 sin anuncios molestos.
   - Lector continuo de páginas de manga de alta resolución.
3. **Descargas Offline**:
   - Descarga capítulos enteros de manga o episodios de anime a tu almacenamiento para verlos sin conexión a internet ni gastar datos móviles.
4. **Bypass de DNS y Bloqueos de ISP**:
   - Integra resolución directa contra los servidores DNS de Google (`8.8.8.8`) y Cloudflare (`1.1.1.1`), evitando que proveedores de internet bloqueen el acceso a los repositorios de GitHub o servidores de streaming.

---

## 💻 Cómo volver a compilar

Si realizas modificaciones en el código fuente, puedes recompilar todo ejecutando:
```cmd
build-all.bat
```
o mediante terminal:
```bash
./gradlew.bat :desktop:fatJar :app:assembleDebug
```
