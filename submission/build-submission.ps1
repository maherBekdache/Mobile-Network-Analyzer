param(
    [string]$NodeVersion = "",
    [string]$OutputName = "MobileNetworkAnalyzer"
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function New-CleanDirectory([string]$Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

function Copy-Tree([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    $null = robocopy $Source $Destination /E /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) {
        throw "Robocopy failed while copying $Source to $Destination"
    }
}

function Invoke-Checked([scriptblock]$Action, [string]$FailureMessage) {
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$buildRoot = Join-Path $PSScriptRoot "build"
$distRoot = Join-Path $PSScriptRoot "dist"
$bundleName = "MobileNetworkAnalyzerApp"
$bundleRoot = Join-Path $buildRoot $bundleName
$cacheRoot = Join-Path $PSScriptRoot "cache"
$pythonBuildRoot = Join-Path $buildRoot "pyinstaller"
$pythonDistRoot = Join-Path $pythonBuildRoot "dist"
$pythonWorkRoot = Join-Path $pythonBuildRoot "work"
$pythonSpecRoot = Join-Path $pythonBuildRoot "spec"
$nodeVersion = if ($NodeVersion) { $NodeVersion } else { (& node -v).Trim().TrimStart("v") }
$nodeFolderName = "node-v$nodeVersion-win-x64"
$nodeZipName = "$nodeFolderName.zip"
$nodeZipPath = Join-Path $cacheRoot $nodeZipName
$nodeUrl = "https://nodejs.org/dist/v$nodeVersion/$nodeZipName"
$professorExe = Join-Path $distRoot "Launch-Mobile-Network-Analyzer.exe"
$apkSource = Join-Path $repoRoot "android\app\build\outputs\apk\debug\app-debug.apk"
$apkTarget = Join-Path $distRoot "$OutputName.apk"

Write-Step "Preparing submission directories"
New-CleanDirectory $buildRoot
New-CleanDirectory $distRoot
New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
New-Item -ItemType Directory -Path $bundleRoot -Force | Out-Null

Write-Step "Building frontend dashboard"
Push-Location (Join-Path $repoRoot "frontend")
try {
    Invoke-Checked { npm.cmd run build } "Frontend build failed."
}
finally {
    Pop-Location
}

Write-Step "Building Android APK"
Push-Location (Join-Path $repoRoot "android")
try {
    Invoke-Checked { .\gradlew.bat assembleDebug } "Android APK build failed."
}
finally {
    Pop-Location
}

Write-Step "Downloading portable Node runtime $nodeVersion"
if (-not (Test-Path $nodeZipPath)) {
    Invoke-WebRequest -Uri $nodeUrl -OutFile $nodeZipPath
}

Write-Step "Assembling application package"
New-Item -ItemType Directory -Path (Join-Path $bundleRoot "app") -Force | Out-Null
Copy-Tree (Join-Path $repoRoot "backend") (Join-Path $bundleRoot "app\backend")
Copy-Tree (Join-Path $repoRoot "frontend\dist") (Join-Path $bundleRoot "app\frontend-dist")
Copy-Item (Join-Path $PSScriptRoot "server\submission-server.mjs") (Join-Path $bundleRoot "app\backend\submission-server.mjs") -Force

$runtimeRoot = Join-Path $bundleRoot "runtime"
Expand-Archive -LiteralPath $nodeZipPath -DestinationPath $runtimeRoot -Force
Rename-Item -LiteralPath (Join-Path $runtimeRoot $nodeFolderName) -NewName "node-win-x64"

Copy-Item (Join-Path $PSScriptRoot "templates\Launch-Mobile-Network-Analyzer.cmd") (Join-Path $bundleRoot "Launch-Mobile-Network-Analyzer.cmd") -Force

@"
Application package generated on $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

1. Install $OutputName.apk on the Android phone.
2. Connect the phone and this laptop to the same Wi-Fi.
3. Double-click Launch-Mobile-Network-Analyzer.exe.
4. Enter the printed LAN URL inside the Android app.
5. Keep the launcher window open during the demo.
"@ | Set-Content -LiteralPath (Join-Path $bundleRoot "START-HERE.txt") -Encoding ASCII

Write-Step "Creating APK copy"
Copy-Item $apkSource $apkTarget -Force

Write-Step "Ensuring PyInstaller is available"
Push-Location $repoRoot
try {
    cmd.exe /c "py -m pip show pyinstaller >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
        Invoke-Checked { py -m pip install --user pyinstaller } "PyInstaller installation failed."
    }
}
finally {
    Pop-Location
}

Write-Step "Creating single-click launcher EXE"
New-CleanDirectory $pythonBuildRoot
New-Item -ItemType Directory -Path $pythonDistRoot -Force | Out-Null
New-Item -ItemType Directory -Path $pythonWorkRoot -Force | Out-Null
New-Item -ItemType Directory -Path $pythonSpecRoot -Force | Out-Null
$pyInstallerArgs = @(
    "-m", "PyInstaller",
    "--noconfirm",
    "--clean",
    "--onefile",
    "--console",
    "--name", "Launch-Mobile-Network-Analyzer",
    "--distpath", $pythonDistRoot,
    "--workpath", $pythonWorkRoot,
    "--specpath", $pythonSpecRoot,
    "--add-data", "$bundleRoot;$bundleName",
    (Join-Path $PSScriptRoot "server\app_launcher.py")
)
Invoke-Checked { py @pyInstallerArgs } "PyInstaller failed to build the launcher EXE."
Copy-Item (Join-Path $pythonDistRoot "Launch-Mobile-Network-Analyzer.exe") $professorExe -Force

if (-not (Test-Path $professorExe)) {
    throw "PyInstaller did not produce the launcher EXE."
}

Write-Step "Copying packaged app folder for backup"
Copy-Tree $bundleRoot (Join-Path $distRoot $bundleName)

Write-Step "Application package is ready"
Write-Host "APK: $apkTarget"
Write-Host "Launcher EXE: $professorExe"
Write-Host "App folder: $(Join-Path $distRoot $bundleName)"
