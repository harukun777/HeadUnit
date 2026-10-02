$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = [System.Drawing.Image]::FromFile((Join-Path $repoRoot 'assets/branding/retdrive-icon.png'))
$background = [System.Drawing.Color]::FromArgb(255, 16, 36, 49)

function Export-Icon([string]$destination, [int]$size, [bool]$round, [double]$scale) {
    $bitmap = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        if ($round) {
            $circle = New-Object System.Drawing.Drawing2D.GraphicsPath
            try {
                $circle.AddEllipse(0, 0, $size, $size)
                $graphics.SetClip($circle)
            } finally { $circle.Dispose() }
        }
        $brush = New-Object System.Drawing.SolidBrush($background)
        try { $graphics.FillRectangle($brush, 0, 0, $size, $size) } finally { $brush.Dispose() }
        $edge = [int]($size * $scale)
        $offset = [int](($size - $edge) / 2)
        $graphics.DrawImage($source, $offset, $offset, $edge, $edge)
        $bitmap.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}

try {
    foreach ($entry in @(@('mdpi',48,108), @('hdpi',72,162), @('xhdpi',96,216), @('xxhdpi',144,324), @('xxxhdpi',192,432))) {
        $directory = Join-Path $repoRoot ('app/src/main/res/mipmap-' + $entry[0])
        foreach ($name in @('ic_launcher','ic_launcher_round','ic_launcher_foreground')) {
            $old = [System.IO.Path]::GetFullPath((Join-Path $directory ($name + '.webp')))
            if (!$old.StartsWith($repoRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw 'Icon path escaped the workspace'
            }
            if (Test-Path -LiteralPath $old) { Remove-Item -LiteralPath $old }
            $round = $name -eq 'ic_launcher_round'
            $adaptive = $name -eq 'ic_launcher_foreground'
            $size = if ($adaptive) { $entry[2] } else { $entry[1] }
            # Leave room for adaptive mask cropping (108dp foreground, 72dp viewport).
            $scale = if ($adaptive) { 0.78 } else { 1.0 }
            Export-Icon (Join-Path $directory ($name + '.png')) $size $round $scale
        }
    }
    Export-Icon (Join-Path $repoRoot 'app/src/main/ic_launcher-playstore.png') 512 $false 1.0
    Export-Icon (Join-Path $repoRoot 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_playstore.png') 512 $false 1.0
    Export-Icon (Join-Path $repoRoot 'assets/branding/retdrive-preview.png') 192 $true 1.0
} finally { $source.Dispose() }
