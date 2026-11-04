Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$rootDir = (Get-Item $scriptDir).Parent.FullName
$distDir = Join-Path $rootDir "dist"

$jarSource = Join-Path $distDir "AniyomiCross-PC.jar"
$icoSource = Join-Path $distDir "icon.ico"
if (-not (Test-Path $jarSource)) {
    $jarSource = Join-Path $scriptDir "AniyomiCross-PC.jar"
    $icoSource = Join-Path $scriptDir "icon.ico"
}

$installDir = Join-Path $env:LOCALAPPDATA "Programs\AniyomiCross"

$form = New-Object System.Windows.Forms.Form
$form.Text = "Instalador de Aniyomi Cross v1.3.2"
$form.Size = New-Object System.Drawing.Size(560, 420)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.BackColor = [System.Drawing.Color]::FromArgb(11, 14, 23)

if (Test-Path $icoSource) {
    try {
        $form.Icon = New-Object System.Drawing.Icon($icoSource)
    } catch {}
}

# Título Hero
$titleLabel = New-Object System.Windows.Forms.Label
$titleLabel.Text = "ANIYOMI CROSS - PC"
$titleLabel.Font = New-Object System.Drawing.Font("Segoe UI", 18, [System.Drawing.FontStyle]::Bold)
$titleLabel.ForeColor = [System.Drawing.Color]::FromArgb(0, 240, 255)
$titleLabel.Location = New-Object System.Drawing.Point(30, 25)
$titleLabel.Size = New-Object System.Drawing.Size(480, 35)
$form.Controls.Add($titleLabel)

$subLabel = New-Object System.Windows.Forms.Label
$subLabel.Text = "Instalador oficial para Windows • Anime, Manga y Webtoons 100% en Español"
$subLabel.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$subLabel.ForeColor = [System.Drawing.Color]::FromArgb(160, 165, 185)
$subLabel.Location = New-Object System.Drawing.Point(32, 65)
$subLabel.Size = New-Object System.Drawing.Size(480, 25)
$form.Controls.Add($subLabel)

# Cuadro de Directorio de Instalación
$pathLabel = New-Object System.Windows.Forms.Label
$pathLabel.Text = "Carpeta de destino:"
$pathLabel.Font = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
$pathLabel.ForeColor = [System.Drawing.Color]::White
$pathLabel.Location = New-Object System.Drawing.Point(32, 110)
$pathLabel.Size = New-Object System.Drawing.Size(480, 20)
$form.Controls.Add($pathLabel)

$pathBox = New-Object System.Windows.Forms.TextBox
$pathBox.Text = $installDir
$pathBox.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$pathBox.BackColor = [System.Drawing.Color]::FromArgb(26, 29, 45)
$pathBox.ForeColor = [System.Drawing.Color]::White
$pathBox.Location = New-Object System.Drawing.Point(32, 135)
$pathBox.Size = New-Object System.Drawing.Size(400, 26)
$form.Controls.Add($pathBox)

$browseBtn = New-Object System.Windows.Forms.Button
$browseBtn.Text = "Examinar..."
$browseBtn.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$browseBtn.BackColor = [System.Drawing.Color]::FromArgb(40, 45, 68)
$browseBtn.ForeColor = [System.Drawing.Color]::White
$browseBtn.FlatStyle = "Flat"
$browseBtn.FlatAppearance.BorderSize = 0
$browseBtn.Location = New-Object System.Drawing.Point(440, 134)
$browseBtn.Size = New-Object System.Drawing.Size(85, 28)
$browseBtn.Add_Click({
    $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
    $dlg.SelectedPath = $pathBox.Text
    if ($dlg.ShowDialog() -eq "OK") {
        $pathBox.Text = $dlg.SelectedPath
    }
})
$form.Controls.Add($browseBtn)

# Opciones de Acceso Directo
$chkDesktop = New-Object System.Windows.Forms.CheckBox
$chkDesktop.Text = "Crear acceso directo en el Escritorio con icono personalizado"
$chkDesktop.Checked = $true
$chkDesktop.ForeColor = [System.Drawing.Color]::White
$chkDesktop.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$chkDesktop.Location = New-Object System.Drawing.Point(32, 180)
$chkDesktop.Size = New-Object System.Drawing.Size(480, 25)
$form.Controls.Add($chkDesktop)

$chkStartMenu = New-Object System.Windows.Forms.CheckBox
$chkStartMenu.Text = "Agregar al Menú Inicio de Windows (Búsqueda rápida)"
$chkStartMenu.Checked = $true
$chkStartMenu.ForeColor = [System.Drawing.Color]::White
$chkStartMenu.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$chkStartMenu.Location = New-Object System.Drawing.Point(32, 210)
$chkStartMenu.Size = New-Object System.Drawing.Size(480, 25)
$form.Controls.Add($chkStartMenu)

# Estado y Barra de Progreso
$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Text = "Listo para comenzar la instalación."
$statusLabel.ForeColor = [System.Drawing.Color]::FromArgb(139, 92, 246)
$statusLabel.Font = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
$statusLabel.Location = New-Object System.Drawing.Point(32, 255)
$statusLabel.Size = New-Object System.Drawing.Size(480, 20)
$form.Controls.Add($statusLabel)

$pBar = New-Object System.Windows.Forms.ProgressBar
$pBar.Location = New-Object System.Drawing.Point(32, 280)
$pBar.Size = New-Object System.Drawing.Size(490, 14)
$pBar.Visible = $false
$form.Controls.Add($pBar)

# Botones de Acción
$installBtn = New-Object System.Windows.Forms.Button
$installBtn.Text = "Instalar Aniyomi Cross"
$installBtn.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$installBtn.BackColor = [System.Drawing.Color]::FromArgb(255, 42, 109)
$installBtn.ForeColor = [System.Drawing.Color]::White
$installBtn.FlatStyle = "Flat"
$installBtn.FlatAppearance.BorderSize = 0
$installBtn.Location = New-Object System.Drawing.Point(280, 320)
$installBtn.Size = New-Object System.Drawing.Size(242, 42)

$cancelBtn = New-Object System.Windows.Forms.Button
$cancelBtn.Text = "Cancelar"
$cancelBtn.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$cancelBtn.BackColor = [System.Drawing.Color]::FromArgb(40, 45, 68)
$cancelBtn.ForeColor = [System.Drawing.Color]::White
$cancelBtn.FlatStyle = "Flat"
$cancelBtn.FlatAppearance.BorderSize = 0
$cancelBtn.Location = New-Object System.Drawing.Point(170, 320)
$cancelBtn.Size = New-Object System.Drawing.Size(100, 42)
$cancelBtn.Add_Click({ $form.Close() })

$installBtn.Add_Click({
    $installBtn.Enabled = $false
    $browseBtn.Enabled = $false
    $pathBox.Enabled = $false
    $pBar.Visible = $true
    $pBar.Value = 20

    $targetDir = $pathBox.Text
    $statusLabel.Text = "Creando carpetas y copiando archivos..."
    $form.Update()

    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    $destJar = Join-Path $targetDir "AniyomiCross-PC.jar"
    $destIco = Join-Path $targetDir "icon.ico"

    Copy-Item $jarSource $destJar -Force
    if (Test-Path $icoSource) {
        Copy-Item $icoSource $destIco -Force
    }
    $pBar.Value = 60
    $statusLabel.Text = "Configurando accesos directos de Windows..."
    $form.Update()

    # Script de ejecución nativo
    $batContent = @"
@echo off
start javaw -jar "$destJar"
"@
    $destBat = Join-Path $targetDir "AniyomiCross.bat"
    Set-Content -Path $destBat -Value $batContent

    $wsh = New-Object -ComObject WScript.Shell

    if ($chkDesktop.Checked) {
        $desktopPath = [System.Environment]::GetFolderPath("Desktop")
        $shortcut = $wsh.CreateShortcut((Join-Path $desktopPath "Aniyomi Cross - PC.lnk"))
        $shortcut.TargetPath = "javaw.exe"
        $shortcut.Arguments = "-jar `"$destJar`""
        $shortcut.WorkingDirectory = $targetDir
        if (Test-Path $destIco) {
            $shortcut.IconLocation = "$destIco,0"
        }
        $shortcut.Save()
    }

    if ($chkStartMenu.Checked) {
        $startMenuPath = [System.Environment]::GetFolderPath("StartMenu")
        $programsPath = Join-Path $startMenuPath "Programs"
        $shortcut = $wsh.CreateShortcut((Join-Path $programsPath "Aniyomi Cross.lnk"))
        $shortcut.TargetPath = "javaw.exe"
        $shortcut.Arguments = "-jar `"$destJar`""
        $shortcut.WorkingDirectory = $targetDir
        if (Test-Path $destIco) {
            $shortcut.IconLocation = "$destIco,0"
        }
        $shortcut.Save()
    }

    $pBar.Value = 100
    $statusLabel.Text = "¡Instalación completada con éxito!"
    $installBtn.Text = "Abrir Aniyomi Cross"
    $installBtn.BackColor = [System.Drawing.Color]::FromArgb(16, 185, 129)
    $installBtn.Enabled = $true
    $cancelBtn.Text = "Salir"

    $installBtn.Add_Click({
        Start-Process "javaw.exe" -ArgumentList "-jar `"$destJar`"" -WorkingDirectory $targetDir
        $form.Close()
    })
})

$form.Controls.Add($cancelBtn)
$form.Controls.Add($installBtn)

$form.ShowDialog() | Out-Null
