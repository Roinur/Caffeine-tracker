[CmdletBinding()]
param(
    [string]$Device,
    [int]$PressX = -1,
    [int]$PressY = -1,
    [string]$Name = ("github-media-{0:yyyyMMdd-HHmmss}.png" -f (Get-Date))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$adb = if ($env:CAFFEINE_ADB) { $env:CAFFEINE_ADB } elseif ($sdk) { Join-Path $sdk 'platform-tools/adb.exe' } else { 'adb' }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$reviewRoot = Join-Path $repoRoot 'build\github-media-review'
$outputPath = [IO.Path]::GetFullPath((Join-Path $reviewRoot $Name))

if (-not $outputPath.StartsWith($reviewRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Review captures must remain inside build/github-media-review.'
}
if ([IO.Path]::GetExtension($outputPath) -ne '.png') {
    throw 'GitHub media captures must use the .png extension.'
}

if (-not $Device) {
    $deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "`tdevice$" })
    if ($deviceLines.Count -ne 1) {
        throw "Expected one authorized adb device, found $($deviceLines.Count). Use -Device when needed."
    }
    $Device = $deviceLines[0] -replace "`tdevice$", ''
}

# ActivityManager can report the new activity while the launcher's last frame is still visible.
# Wait for accessibility idle and require the actual UI root before considering a capture.
& $adb -s $Device shell uiautomator dump /sdcard/caffeine-capture-guard.xml | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not verify the visible app. Capture refused.' }
[xml]$visibleUi = (& $adb -s $Device shell cat /sdcard/caffeine-capture-guard.xml) -join "`n"
& $adb -s $Device shell rm -f /sdcard/caffeine-capture-guard.xml | Out-Null
$visibleRoots = @($visibleUi.SelectNodes('/hierarchy/node'))
if ($visibleRoots.Count -eq 0 -or @($visibleRoots | Where-Object { $_.package -ne 'com.roinur.caffeinetracker' }).Count -gt 0) {
    throw 'The visible UI is not Caffeine Tracker. Capture refused.'
}
$activities = (& $adb -s $Device shell dumpsys activity activities) -join "`n"
$topMatch = [regex]::Match(
    $activities,
    'topResumedActivity=.*com\.roinur\.caffeinetracker/[^\s}]+\s+t(\d+)'
)
if (-not $topMatch.Success) {
    throw 'GitHub media mode is not the foreground activity. Capture refused.'
}
$taskId = [regex]::Escape($topMatch.Groups[1].Value)
$githubLauncherInForegroundTask =
    $activities -match "com\.roinur\.caffeinetracker/(?:com\.roinur\.caffeinetracker)?\.app\.GitHubMediaLauncher\s+t$taskId}"
if (-not $githubLauncherInForegroundTask) {
    throw 'The foreground Caffeine Tracker task was not launched in GitHub media mode. Capture refused.'
}

New-Item -ItemType Directory -Path $reviewRoot -Force | Out-Null
$remotePath = '/sdcard/caffeine-github-media-review.png'
$rawPath = Join-Path $reviewRoot '.raw-github-media.png'

if (($PressX -ge 0) -ne ($PressY -ge 0)) { throw 'Specify both PressX and PressY.' }
$pressed = $false
try {
    if ($PressX -ge 0) {
        $pressed = $true
        & $adb -s $Device shell input motionevent DOWN $PressX $PressY
        if ($LASTEXITCODE -ne 0) { throw 'Could not press the review target.' }
        Start-Sleep -Milliseconds 140
    }
    & $adb -s $Device shell screencap -p $remotePath
    if ($LASTEXITCODE -ne 0) { throw 'ADB screencap failed.' }
    if ($pressed) {
        & $adb -s $Device shell input motionevent UP $PressX $PressY
        $pressed = $false
    }
    & $adb -s $Device pull $remotePath $rawPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not pull the ADB capture.' }

    Add-Type -AssemblyName System.Drawing
    $source = [Drawing.Bitmap]::FromFile($rawPath)
    try {
        $windowDump = (& $adb -s $Device shell dumpsys window) -join "`n"
        $topCrop = 0
        $bottomCrop = 0

        $statusMatches = [regex]::Matches(
            $windowDump,
            'type=statusBars frame=\[0,0\]\[(\d+),(\d+)\] visible=true'
        )
        foreach ($match in $statusMatches) {
            if ([int]$match.Groups[1].Value -eq $source.Width) {
                $topCrop = [Math]::Max($topCrop, [int]$match.Groups[2].Value)
            }
        }

        $navigationMatches = [regex]::Matches(
            $windowDump,
            'type=navigationBars frame=\[0,(\d+)\]\[(\d+),(\d+)\] visible=true'
        )
        foreach ($match in $navigationMatches) {
            $navTop = [int]$match.Groups[1].Value
            $navWidth = [int]$match.Groups[2].Value
            $navBottom = [int]$match.Groups[3].Value
            if ($navWidth -eq $source.Width -and $navBottom -eq $source.Height -and $navTop -gt 0) {
                $bottomCrop = [Math]::Max($bottomCrop, $source.Height - $navTop)
            }
        }

        if ($topCrop -le 0) { throw 'Could not determine the Android status-bar inset.' }
        $captureHeight = $source.Height - $topCrop - $bottomCrop
        if ($captureHeight -le 0) { throw 'Calculated capture bounds are invalid.' }

        $bounds = [Drawing.Rectangle]::new(0, $topCrop, $source.Width, $captureHeight)
        $cropped = $source.Clone($bounds, $source.PixelFormat)
        try {
            $cropped.Save($outputPath, [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $cropped.Dispose()
        }
    } finally {
        $source.Dispose()
    }
} finally {
    if ($pressed) { & $adb -s $Device shell input motionevent UP $PressX $PressY | Out-Null }
    & $adb -s $Device shell rm -f $remotePath | Out-Null
    Remove-Item -LiteralPath $rawPath -Force -ErrorAction SilentlyContinue
}

Write-Host "Private review capture saved: $outputPath" -ForegroundColor Green
Write-Host 'The status/navigation bars were excluded. This file is below build and is not ready for GitHub publication.'
