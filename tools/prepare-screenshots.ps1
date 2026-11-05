<#
.SYNOPSIS
Prepares phone screenshots for the Google Play listing.

.DESCRIPTION
Google Play takes screenshots whose long side is at most twice the short side, without
transparency. Most phones take taller ones (for example 1080 x 2340), so this crops the extra
height, mostly from the status bar at the top, and saves numbered JPEG files where the store
listing keeps them. Needs only PowerShell 7 on Windows; nothing to install.

  ./tools/prepare-screenshots.ps1 -Path C:\Users\me\Downloads\Screenshot_*.png
#>
param(
  [Parameter(Mandatory)][string[]]$Path,
  [string]$Out = (Join-Path $PSScriptRoot '..\fastlane\metadata\android\en-US\images\phoneScreenshots')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$quality = [System.Drawing.Imaging.EncoderParameters]::new(1)
$quality.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]92)

$number = 1
foreach ($file in Get-ChildItem -Path $Path -File | Sort-Object Name) {
  $image = [System.Drawing.Image]::FromFile($file.FullName)
  try {
    $width = $image.Width
    $height = $image.Height
    $short = [Math]::Min($width, $height)
    $long = [Math]::Max($width, $height)
    $crop = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
    if ($long -gt 2 * $short) {
      $excess = $long - 2 * $short
      $first = [int][Math]::Round($excess * 0.6)
      $crop = if ($height -ge $width) {
        [System.Drawing.Rectangle]::new(0, $first, $width, $height - $excess)
      } else {
        [System.Drawing.Rectangle]::new($first, 0, $width - $excess, $height)
      }
    }
    $result = [System.Drawing.Bitmap]::new($crop.Width, $crop.Height, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    try {
      $graphics = [System.Drawing.Graphics]::FromImage($result)
      $graphics.Clear([System.Drawing.Color]::White)
      $graphics.DrawImage($image, [System.Drawing.Rectangle]::new(0, 0, $crop.Width, $crop.Height), $crop, [System.Drawing.GraphicsUnit]::Pixel)
      $graphics.Dispose()
      $target = Join-Path $Out ('{0}.jpg' -f $number)
      $result.Save($target, $jpeg, $quality)
      '{0} ({1}x{2}) -> {3} ({4}x{5})' -f $file.Name, $width, $height, $target, $crop.Width, $crop.Height
      $number++
    } finally { $result.Dispose() }
  } finally { $image.Dispose() }
}
if ($number -eq 1) { Write-Warning 'No screenshots found.' }
