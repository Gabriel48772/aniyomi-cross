package com.aniyomi.cross.desktop

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

object IconGenerator {

    fun generateAppLogo(size: Int, isRound: Boolean = false): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val s = size.toFloat()
        val pad = s * 0.04f

        // 1. Fondo estilizado (Gradiente Obsidian + Violeta Oscuro)
        val bgShape: Shape = if (isRound) {
            Ellipse2D.Float(pad, pad, s - pad * 2, s - pad * 2)
        } else {
            RoundRectangle2D.Float(pad, pad, s - pad * 2, s - pad * 2, s * 0.28f, s * 0.28f)
        }

        val bgPaint = RadialGradientPaint(
            Point2D.Float(s * 0.5f, s * 0.35f),
            s * 0.7f,
            floatArrayOf(0.0f, 0.65f, 1.0f),
            arrayOf(
                Color(0x1F, 0x1A, 0x3A), // Dark Royal Violet
                Color(0x0E, 0x0F, 0x1A), // Deep Obsidian
                Color(0x07, 0x08, 0x10)  // Abyss Black
            )
        )
        g.paint = bgPaint
        g.fill(bgShape)

        // Borde exterior Neón Neomórfico
        g.paint = LinearGradientPaint(
            Point2D.Float(0f, 0f),
            Point2D.Float(s, s),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            arrayOf(
                Color(0xFF, 0x2A, 0x6D, 220), // Neon Crimson
                Color(0x8B, 0x5C, 0xF6, 180), // Electric Violet
                Color(0x00, 0xF0, 0xFF, 240)  // Cyber Cyan
            )
        )
        g.stroke = BasicStroke(s * 0.022f)
        g.draw(bgShape)

        // 2. Anillo de energía orbital brillante
        val ringArea = Ellipse2D.Float(s * 0.16f, s * 0.16f, s * 0.68f, s * 0.68f)
        g.paint = RadialGradientPaint(
            Point2D.Float(s * 0.5f, s * 0.5f),
            s * 0.45f,
            floatArrayOf(0.0f, 0.85f, 1.0f),
            arrayOf(
                Color(0x8B, 0x5C, 0xF6, 40),
                Color(0xFF, 0x2A, 0x6D, 60),
                Color(0x00, 0xF0, 0xFF, 0)
            )
        )
        g.fill(ringArea)

        // 3. Emblema "A" Estilizada con Fusión Cinema Play & Manga Slash
        // Lado izquierdo del triángulo "A" (Cian Neón)
        val leftLeg = Path2D.Float()
        leftLeg.moveTo(s * 0.50f, s * 0.20f)
        leftLeg.lineTo(s * 0.22f, s * 0.78f)
        leftLeg.lineTo(s * 0.35f, s * 0.78f)
        leftLeg.lineTo(s * 0.50f, s * 0.44f)
        leftLeg.closePath()

        g.paint = LinearGradientPaint(
            Point2D.Float(s * 0.50f, s * 0.20f),
            Point2D.Float(s * 0.22f, s * 0.78f),
            floatArrayOf(0.0f, 1.0f),
            arrayOf(Color(0xFF, 0xFF, 0xFF), Color(0x00, 0xE5, 0xFF))
        )
        g.fill(leftLeg)

        // Lado derecho del triángulo "A" y Símbolo Play (Magenta Neón)
        val rightLeg = Path2D.Float()
        rightLeg.moveTo(s * 0.50f, s * 0.20f)
        rightLeg.lineTo(s * 0.78f, s * 0.78f)
        rightLeg.lineTo(s * 0.65f, s * 0.78f)
        rightLeg.lineTo(s * 0.50f, s * 0.44f)
        rightLeg.closePath()

        g.paint = LinearGradientPaint(
            Point2D.Float(s * 0.50f, s * 0.20f),
            Point2D.Float(s * 0.78f, s * 0.78f),
            floatArrayOf(0.0f, 1.0f),
            arrayOf(Color(0xFF, 0x4B, 0x87), Color(0xFF, 0x2A, 0x6D))
        )
        g.fill(rightLeg)

        // Barra transversal en forma de Triángulo Play Cinema (▶)
        val playGlyph = Path2D.Float()
        playGlyph.moveTo(s * 0.38f, s * 0.48f)
        playGlyph.lineTo(s * 0.66f, s * 0.62f)
        playGlyph.lineTo(s * 0.38f, s * 0.76f)
        playGlyph.closePath()

        g.paint = LinearGradientPaint(
            Point2D.Float(s * 0.38f, s * 0.48f),
            Point2D.Float(s * 0.66f, s * 0.62f),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            arrayOf(
                Color(0x00, 0xF0, 0xFF),
                Color(0xFF, 0xFF, 0xFF),
                Color(0xFF, 0x2A, 0x6D)
            )
        )
        g.fill(playGlyph)

        // 4. Katana Slash Luminoso / Cruz dinámica (Aniyomi CROSS)
        val crossSlash = Path2D.Float()
        crossSlash.moveTo(s * 0.20f, s * 0.70f)
        crossSlash.lineTo(s * 0.80f, s * 0.34f)
        crossSlash.lineTo(s * 0.82f, s * 0.37f)
        crossSlash.lineTo(s * 0.22f, s * 0.73f)
        crossSlash.closePath()

        g.paint = LinearGradientPaint(
            Point2D.Float(s * 0.20f, s * 0.70f),
            Point2D.Float(s * 0.80f, s * 0.34f),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            arrayOf(
                Color(0x00, 0xF0, 0xFF, 0),
                Color(0xFF, 0xFF, 0xFF, 255),
                Color(0xFF, 0x2A, 0x6D, 0)
            )
        )
        g.fill(crossSlash)

        // 5. Destello central estelar
        val sparkle = Ellipse2D.Float(s * 0.485f, s * 0.485f, s * 0.03f, s * 0.03f)
        g.color = Color.WHITE
        g.fill(sparkle)

        g.dispose()
        return image
    }

    fun createIcoBytes(pngImage: BufferedImage): ByteArray {
        val pngOut = ByteArrayOutputStream()
        ImageIO.write(pngImage, "PNG", pngOut)
        val pngBytes = pngOut.toByteArray()

        val icoOut = ByteArrayOutputStream()
        // ICO Header: 6 bytes
        icoOut.write(0) // Reserved
        icoOut.write(0)
        icoOut.write(1) // Type: 1 = ICO
        icoOut.write(0)
        icoOut.write(1) // Number of images: 1
        icoOut.write(0)

        // Directory Entry: 16 bytes
        val w = if (pngImage.width >= 256) 0 else pngImage.width
        val h = if (pngImage.height >= 256) 0 else pngImage.height
        icoOut.write(w)
        icoOut.write(h)
        icoOut.write(0) // Color count (0 = no palette)
        icoOut.write(0) // Reserved
        icoOut.write(1) // Color planes: 1
        icoOut.write(0)
        icoOut.write(32) // Bits per pixel: 32
        icoOut.write(0)

        // Size of image data (4 bytes, little-endian)
        val size = pngBytes.size
        icoOut.write(size and 0xFF)
        icoOut.write((size shr 8) and 0xFF)
        icoOut.write((size shr 16) and 0xFF)
        icoOut.write((size shr 24) and 0xFF)

        // Offset of image data (4 bytes, little-endian) = 6 + 16 = 22
        val offset = 22
        icoOut.write(offset and 0xFF)
        icoOut.write((offset shr 8) and 0xFF)
        icoOut.write((offset shr 16) and 0xFF)
        icoOut.write((offset shr 24) and 0xFF)

        // PNG Data
        icoOut.write(pngBytes)
        return icoOut.toByteArray()
    }

    fun exportAllIcons(baseDir: File) {
        val desktopIconDir = File(baseDir, "desktop/src/main/resources/icons")
        desktopIconDir.mkdirs()

        // 1. Iconos para PC
        val logo512 = generateAppLogo(512, isRound = false)
        val logo256 = generateAppLogo(256, isRound = false)
        ImageIO.write(logo512, "PNG", File(desktopIconDir, "icon.png"))
        ImageIO.write(logo256, "PNG", File(desktopIconDir, "icon_256.png"))

        val icoBytes = createIcoBytes(logo256)
        File(desktopIconDir, "icon.ico").writeBytes(icoBytes)

        // También colocar icono en la carpeta raíz y en dist para los instaladores
        File(baseDir, "dist").mkdirs()
        File(baseDir, "dist/icon.ico").writeBytes(icoBytes)
        ImageIO.write(logo256, "PNG", File(baseDir, "dist/icon.png"))

        // 2. Iconos para Android (Mipmaps)
        val mipmaps = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )

        for ((folder, dim) in mipmaps) {
            val dir = File(baseDir, "app/src/main/res/$folder")
            dir.mkdirs()
            val normal = generateAppLogo(dim, isRound = false)
            val round = generateAppLogo(dim, isRound = true)

            ImageIO.write(normal, "PNG", File(dir, "ic_launcher.png"))
            ImageIO.write(round, "PNG", File(dir, "ic_launcher_round.png"))

            // Eliminar los webp antiguos si existen para que tome el png actualizado
            File(dir, "ic_launcher.webp").delete()
            File(dir, "ic_launcher_round.webp").delete()
        }
        println("Iconos exportados con éxito.")
    }
}

fun main() {
    var root = File(".").canonicalFile
    while (root.parentFile != null && !File(root, "settings.gradle.kts").exists()) {
        root = root.parentFile
    }
    // Si ya estamos dentro del directorio raíz
    if (!File(root, "settings.gradle.kts").exists() && File(root.parentFile, "settings.gradle.kts").exists()) {
        root = root.parentFile
    }
    println("Generando iconos en directorio raíz: ${root.absolutePath}")
    IconGenerator.exportAllIcons(root)
}
