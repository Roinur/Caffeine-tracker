[CmdletBinding()]
param([switch]$Checks, [switch]$Install, [switch]$Online, [switch]$Offline, [switch]$Release, [switch]$NoBump)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
# Use the checked-in Gradle wrapper and the SDK selected by the caller.
if ($Online -and $Offline) { throw 'Choose either -Online or -Offline.' }
$gradle = Join-Path $projectRoot 'gradlew.bat'
$signingNames = 'CAFFEINE_KEYSTORE', 'CAFFEINE_STORE_PASSWORD', 'CAFFEINE_KEY_ALIAS', 'CAFFEINE_KEY_PASSWORD'
$previousSigning = @{}
foreach ($name in $signingNames) { $previousSigning[$name] = [Environment]::GetEnvironmentVariable($name) }
$credentials = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/caffeine-tracker-release/signing.clixml'
if ($Release -and -not $env:CAFFEINE_KEYSTORE -and (Test-Path -LiteralPath $credentials)) {
    $localKey = Import-Clixml -LiteralPath $credentials
    $env:CAFFEINE_KEYSTORE = Join-Path (Split-Path -Parent $credentials) 'caffeine-tracker.p12'
    $env:CAFFEINE_KEY_ALIAS = $localKey.UserName
    $env:CAFFEINE_STORE_PASSWORD = $localKey.GetNetworkCredential().Password
    $env:CAFFEINE_KEY_PASSWORD = $env:CAFFEINE_STORE_PASSWORD
}
try {
    if ($Release) {
        foreach ($name in $signingNames) {
            if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing release-signing configuration: $name" }
        }
    }
    $androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    $localSdk = Join-Path $projectRoot 'local.properties'
    if (-not $androidSdk -and (Test-Path -LiteralPath $localSdk)) {
        $sdkLine = Select-String -LiteralPath $localSdk -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($sdkLine) { $androidSdk = $sdkLine.Matches[0].Groups[1].Value.Replace('\:', ':').Replace('\\', '\') }
    }
    if ($Install -and -not $androidSdk) { throw 'Set ANDROID_HOME or sdk.dir before installing.' }
    $versionName = (Select-String -LiteralPath (Join-Path $projectRoot 'app\build.gradle.kts') -Pattern 'versionName\s*=\s*"([^"]+)"' |
        Select-Object -First 1).Matches.Groups[1].Value
    if (-not $versionName) { $versionName = '1.0' }
    if ($Release) {
        $tasks = @(':app:assembleRelease')
        if ($Checks) { $tasks += @(':app:testReleaseUnitTest', ':app:lintRelease') }
        $builtApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
        $apkName = "Caffeine-Tracker-$versionName-release.apk"
    } else {
        $tasks = @(':app:assembleDebug')
        if ($Checks) { $tasks += @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebugAndroidTest') }
        $builtApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
        $apkName = "Caffeine-Tracker-$versionName-debug.apk"
    }
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $buildNumberFile = Join-Path $projectRoot 'build-number.properties'
    # Hold the file open so two concurrent script builds cannot allocate the same number.
    $lockPath = Join-Path $projectRoot '.gradle/caffeine-build-number.lock'
    [IO.Directory]::CreateDirectory((Split-Path -Parent $lockPath)) | Out-Null
    $buildLock = [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    try {
        $previousBuildNumberContent = $null
        $currentVersionCode = $null
        if (-not $NoBump) {
            $previousBuildNumberContent = [System.IO.File]::ReadAllText($buildNumberFile, $utf8NoBom)
            if ($previousBuildNumberContent -match '(?m)^versionCode=(\d+)\s*$') {
                $currentVersionCode = [long]$Matches[1] + 1
                if ($currentVersionCode -gt 2100000000) { throw 'Android versionCode limit reached.' }
                $updatedBuildNumberContent = [regex]::Replace($previousBuildNumberContent, '(?m)^versionCode=\d+', "versionCode=$currentVersionCode")
                [System.IO.File]::WriteAllText($buildNumberFile, $updatedBuildNumberContent, $utf8NoBom)
                Write-Host "Version:       $versionName"
                Write-Host "Build number:  $currentVersionCode"
            } else { throw 'build-number.properties must contain a valid versionCode.' }
        }
        $argsList = @('--no-daemon','--console=plain','--max-workers=1','--build-cache')
        if ($Offline) { $argsList += '--offline' }
        $buildSucceeded = $false
        Push-Location $projectRoot
        try {
            & $gradle @argsList @tasks
            if ($LASTEXITCODE -ne 0) { throw 'Bygget misslyckades.' }
            if (-not (Test-Path -LiteralPath $builtApk -PathType Leaf)) {
                throw "Bygget slutfördes men förväntad APK saknas: $builtApk"
            }
            $buildSucceeded = $true
            $output = Join-Path $projectRoot 'outputs'
            New-Item -ItemType Directory -Path $output -Force | Out-Null
            $apk = Join-Path $output $apkName
            Copy-Item -LiteralPath $builtApk -Destination $apk -Force
            $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $apkName" | Set-Content -LiteralPath ($apk + '.sha256') -Encoding ascii
            if ($Install) {
                $adb = Join-Path $androidSdk 'platform-tools\adb.exe'
                & $adb install -r $apk
                if ($LASTEXITCODE -ne 0) { throw 'Installationen misslyckades.' }
                & $adb shell am start -n com.roinur.caffeinetracker/.app.MainActivity
            }
        } finally {
            if (-not $buildSucceeded -and $null -ne $previousBuildNumberContent) {
                [System.IO.File]::WriteAllText($buildNumberFile, $previousBuildNumberContent, $utf8NoBom)
            }
            Pop-Location
        }
    } finally {
        $buildLock.Dispose()
    }
} finally {
    foreach ($name in $signingNames) { [Environment]::SetEnvironmentVariable($name, $previousSigning[$name]) }
}
