# 🌟 Aniyomi Cross (Multiplataforma PC & Android)

<p align="center">
  <img src="dist/icon.png" width="140" height="140" alt="Aniyomi Cross Logo" style="border-radius: 28px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);" />
</p>

<p align="center">
  <b>La plataforma definitiva todo-en-uno para ver Anime y leer Manga / Manhwa / Webtoons 100% en Español.</b><br>
  Disponible de forma nativa para <b>Teléfonos Android (APK)</b> y para <b>Computadoras (Windows PC)</b>.
</p>

<p align="center">
  <a href="https://github.com/Gabriel48772/aniyomi-cross/releases"><img src="https://img.shields.io/badge/Versi%C3%B3n-1.3.5%20(Estable)-00F0FF?style=for-the-badge&logo=android" alt="Versión"></a>
  <img src="https://img.shields.io/badge/Idioma-100%25%20Espa%C3%B1ol-FF2A6D?style=for-the-badge" alt="Español">
  <img src="https://img.shields.io/badge/Plataformas-Android%20%7C%20Windows-8B5CF6?style=for-the-badge" alt="Plataformas">
</p>

---

## 📥 CENTRO DE DESCARGAS OFICIAL

| Plataforma | Archivo de Descarga | Tipo | Instrucciones Rápidas |
| :--- | :--- | :--- | :--- |
| 📱 **Android (Teléfono)** | [📥 **Descargar APK para Teléfono**](dist/AniyomiCross-Android.apk?raw=true) | `.apk` (v1.3.5) | Descarga en tu celular y pulsa "Instalar". |
| 💻 **PC (Instalador Windows)** | [🚀 **Descargar Instalador para PC**](dist/Instalador-AniyomiCross-PC.bat?raw=true) | `.bat` / `.ps1` | Descarga y haz doble clic para instalar con acceso directo e icono. |
| 💻 **PC (Ejecutable Directo)** | [📦 **Descargar Ejecutable .jar**](dist/AniyomiCross-PC.jar?raw=true) | `.jar` portable | Ejecuta directamente con doble clic o con `run-pc.bat`. |

> [!TIP]
> **Para usuarios de PC**: Recomendamos descargar el **[Instalador para PC](dist/Instalador-AniyomiCross-PC.bat?raw=true)**. Creará automáticamente los accesos directos en tu Escritorio y en tu Menú Inicio con el logo oficial de Aniyomi Cross.

---

## 🆕 Novedades de la Versión 1.3.5

1. **Solución Definitiva de Cierre Repentino (Crash) al Abrir o Descargar Capítulos**:
   - Se erradicó la dependencia en APIs de Java 8+ incompatibles con el entorno móvil (reemplazo de Base64 por decodificador nativo Kotlin `PureBase64` sin dependencias externas).
   - Aislamiento robusto de hilos: la obtención y búsqueda de servidores de anime ahora se ejecuta en `Dispatchers.IO` protegida contra excepciones no controladas o cancelaciones de snackbar.
2. **Estabilidad Absoluta en Reproducción y Descargas**:
   - Tanto la reproducción ("Ver") como la descarga de episodios en segundo plano cuentan con protección contra fallos de inicialización en `PlayerView` y `WebView`.
   - Redirección con un solo toque hacia navegadores y reproductores externos (Chrome, VLC, etc.) mediante selector nativo.

---

## 🛠️ Estructura del Proyecto

```text
aniyomi-multiplatform/
├── core/                       # Núcleo compartido (Multiplataforma)
│   ├── model/                  # Modelos de datos (MediaItem, VideoSource, MangaPage)
│   ├── network/                # Cliente OkHttp con DNS Bypass (8.8.8.8 / 1.1.1.1)
│   ├── scrapers/               # Fuentes directas (AnimeFLV, JKanime, MonosChinos, ZonaTMO, etc.)
│   └── updater/                # Gestor de auto-actualizaciones desde GitHub
├── app/                        # Aplicación Android (Jetpack Compose & Media3 ExoPlayer)
│   └── src/main/
│       ├── res/drawable/       # Iconos adaptive oficiales
│       └── res/mipmap-*/       # Iconos PNG de alta resolución para launcher
├── desktop/                    # Aplicación para PC (Java Swing & Reproductor Cinema 1080p)
│   └── src/main/resources/icons/ # Iconos oficiales .ico y .png
├── dist/                       # Artefactos finales listos para descargar
│   ├── AniyomiCross-Android.apk # APK para celulares Android
│   ├── Instalador-AniyomiCross-PC.bat # Instalador para Windows
│   ├── AniyomiCross-PC.jar     # Ejecutable para PC
│   ├── icon.ico                # Icono para Windows
│   └── icon.png                # Logo oficial
└── installer/
    └── setup.ps1               # Script de instalación gráfica para Windows
```

---

## 💻 Compilación desde Código Fuente

Si deseas compilar los ejecutables tú mismo:
```bash
./gradlew.bat :desktop:fatJar :app:assembleDebug
```
Los archivos resultantes se ubican en:
- PC: `desktop/build/libs/desktop-all.jar` -> `dist/AniyomiCross-PC.jar`
- Android: `app/build/outputs/apk/debug/app-debug.apk` -> `dist/AniyomiCross-Android.apk`
