<#
.SYNOPSIS
  Build the SuperGreenfoot Windows installer (MSI) with jpackage.

.DESCRIPTION
  The Windows counterpart of installer/mac/build-dmg.sh. It stages the same
  application files, links a private runtime and runs jpackage.

  Requires a full JDK 21+ at $env:JAVA_HOME (jlink, jpackage, jmods).
  An MSI additionally needs WiX 3.x (candle.exe and light.exe): JDK 21's
  jpackage cannot drive WiX 4 or 5. Without WiX the script still produces an
  app folder, which is a perfectly good way to hand SuperGreenfoot out.

  The installer is unsigned, so Windows SmartScreen shows a warning on first
  run ("More info" > "Run anyway") until a code-signing certificate exists.

.PARAMETER Out
  Output directory. Default: build\installer-windows.

.PARAMETER NoBuild
  Skip the Gradle build. The Gradle task packageSuperGreenfootWindows passes
  this because it already depends on assemble and userJavadoc.

.PARAMETER AppFolder
  Build only the app folder, never an MSI.

.PARAMETER FullJdk
  Use $env:JAVA_HOME as the runtime instead of a jlinked image (for debugging).

.EXAMPLE
  $env:JAVA_HOME = "C:\Users\me\AppData\Local\Programs\Java\jdk-21.0.12.1+1"
  installer\windows\build-msi.ps1
#>
[CmdletBinding()]
param(
    [string] $Out,
    [switch] $NoBuild,
    [switch] $AppFolder,
    [switch] $FullJdk
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Generated once and fixed for ever: it is what lets a later SuperGreenfoot MSI
# upgrade an installed one in place instead of installing a second copy.
$UpgradeUuid = '9b1987f3-6a5b-4836-a3ae-1305410a6e5f'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Lib  = Join-Path $Root 'greenfoot\build\resources\main\lib'
if (-not $Out) { $Out = Join-Path $Root 'build\installer-windows' }

function Die([string] $Message) { Write-Error $Message; exit 1 }

# ---- JDK ----
if (-not $env:JAVA_HOME) { Die 'set JAVA_HOME to a full JDK 21+' }
$JavaHome = $env:JAVA_HOME
foreach ($tool in 'jlink', 'jpackage') {
    if (-not (Test-Path (Join-Path $JavaHome "bin\$tool.exe"))) { Die "$JavaHome has no $tool" }
}
if (-not (Test-Path (Join-Path $JavaHome 'jmods'))) { Die "$JavaHome has no jmods directory" }

# ---- WiX: needed for an MSI, optional for an app folder ----
function Find-WiX {
    if ((Get-Command candle.exe -ErrorAction SilentlyContinue) -and
        (Get-Command light.exe  -ErrorAction SilentlyContinue)) { return 'PATH' }
    $candidates = @()
    if ($env:WIX) { $candidates += (Join-Path $env:WIX 'bin') }
    $candidates += "$env:LOCALAPPDATA\Programs\WiX314"
    $candidates += "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin"
    $candidates += "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin"
    foreach ($dir in $candidates) {
        if ($dir -and (Test-Path (Join-Path $dir 'candle.exe')) -and (Test-Path (Join-Path $dir 'light.exe'))) {
            return $dir
        }
    }
    return $null
}

$MakeMsi = -not $AppFolder
if ($MakeMsi) {
    $wix = Find-WiX
    if (-not $wix) {
        Write-Warning 'WiX 3 (candle.exe, light.exe) not found; building an app folder instead of an MSI.'
        Write-Warning 'Install WiX 3.14 and re-run to get an installer.'
        $MakeMsi = $false
    }
    elseif ($wix -ne 'PATH') {
        Write-Host "Using WiX at $wix"
        $env:PATH = "$wix;$env:PATH"
    }
}

# ---- 0. make sure the jars match the source ----
# (a stale build once shipped an installer without a committed fix)
if (-not $NoBuild) {
    Write-Host 'Building (gradle :greenfoot:assemble :greenfoot:userJavadoc)...'
    & (Join-Path $Root 'gradlew.bat') ':greenfoot:assemble' ':greenfoot:userJavadoc' -q
    if ($LASTEXITCODE -ne 0) { Die 'Gradle build failed; not packaging stale jars' }
}
if (-not (Test-Path (Join-Path $Lib 'boot.jar'))) {
    Die "run gradlew.bat :greenfoot:assemble first ($Lib\boot.jar missing)"
}
# Compare against the newest shipped jar: a change in bluej/ alone rebuilds
# bluej.jar but leaves greenfoot.jar as it was, which is not stale.
$newestJar = Get-ChildItem (Join-Path $Lib '*.jar') | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
$newer = Get-ChildItem -Recurse -File -ErrorAction SilentlyContinue `
            (Join-Path $Root 'greenfoot\src\main'), (Join-Path $Root 'bluej\src\main') |
         Where-Object { $_.LastWriteTimeUtc -gt $newestJar.LastWriteTimeUtc } | Select-Object -First 1
if ($newer) { Die "Source newer than $($newestJar.Name) (e.g. $($newer.FullName)); rebuild first" }

$gitRev = (& git.exe -C $Root rev-parse --short HEAD 2>$null)
if (-not $gitRev) { $gitRev = 'unknown' }
if (& git.exe -C $Root status --porcelain --untracked-files=no 2>$null) { $gitRev = "$gitRev-dirty" }

$props = @{}
Get-Content (Join-Path $Root 'version.properties') |
    Where-Object { $_ -match '^\s*[^#].*=' } |
    ForEach-Object { $k, $v = $_ -split '=', 2; $props[$k.Trim()] = $v.Trim() }
$Version = $props['supergreenfoot_version']
if (-not $Version) { Die 'supergreenfoot_version missing from version.properties' }

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
$null = New-Item -ItemType Directory -Force -Path (Join-Path $Out 'input'), (Join-Path $Out 'work')
$Out = (Resolve-Path $Out).Path
Write-Host "SuperGreenfoot $Version -> $Out"

# ---- 1. application files (the flattened lib dir) ----
Write-Host 'Staging application files...'
$input_ = Join-Path $Out 'input'
Copy-Item -Recurse -Force (Join-Path $Lib '*') $input_ -Exclude 'testlib'
Remove-Item -Recurse -Force (Join-Path $input_ 'testlib') -ErrorAction SilentlyContinue
$docDir = Join-Path $input_ 'doc'
$null = New-Item -ItemType Directory -Force -Path $docDir
if (Test-Path (Join-Path $Root 'bluej\doc\API')) {
    Copy-Item -Recurse -Force (Join-Path $Root 'bluej\doc\API') $docDir
}
$commonDir = Join-Path $input_ 'greenfoot\common'
$null = New-Item -ItemType Directory -Force -Path $commonDir
Copy-Item -Recurse -Force (Join-Path $Root 'greenfoot\common\*') $commonDir
foreach ($f in 'LICENSE.txt', 'THIRDPARTYLICENSE.txt', 'GREENFOOT_LICENSES.txt') {
    $p = Join-Path $Root "greenfoot\doc\$f"
    if (Test-Path $p) { Copy-Item -Force $p $docDir }
}
$readme = Join-Path $Root 'greenfoot\doc\Greenfoot-README.txt'
if (Test-Path $readme) { Copy-Item -Force $readme (Join-Path $input_ 'README.TXT') }
$stamp = "SuperGreenfoot $Version built $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mmZ')) from $gitRev"
Set-Content -Path (Join-Path $input_ 'supergreenfoot-build.txt') -Value $stamp -Encoding UTF8

# ---- 2. runtime ----
$Runtime = Join-Path $Out 'work\runtime'
if ($FullJdk) {
    Write-Host 'Using the full JDK as runtime...'
    $Runtime = $JavaHome
}
else {
    Write-Host 'Linking runtime (all JDK modules, so nothing the IDE or javac needs is missing)...'
    & (Join-Path $JavaHome 'bin\jlink.exe') --module-path (Join-Path $JavaHome 'jmods') `
        --add-modules ALL-MODULE-PATH --output $Runtime `
        --strip-debug --no-header-files --no-man-pages --compress zip-6
    if ($LASTEXITCODE -ne 0) { Die 'jlink failed' }
}
# No jmods are shipped, matching the macOS installer: the installed IDE builds
# game runtimes by copying its own runtime image (NativePackager.findRuntimeImage).
$size = (Get-ChildItem -Recurse -File $Runtime | Measure-Object -Sum Length).Sum / 1MB
Write-Host ('  runtime size: {0:N0} MB' -f $size)

# ---- 3. jpackage ----
$type = if ($MakeMsi) { 'msi' } else { 'app-image' }
Write-Host "Running jpackage (--type $type)..."
$jp = @(
    '--type', $type
    '--input', $input_
    '--dest', (Join-Path $Out 'work')
    '--name', 'SuperGreenfoot'
    '--app-version', $Version
    '--vendor', 'SuperGreenfoot'
    '--description', 'SuperGreenfoot: Greenfoot with precise actors, real sound, full screen and game export'
    '--main-jar', 'boot.jar'
    '--main-class', 'bluej.Boot'
    '--arguments', '-greenfoot=true'
    '--arguments', '-bluej.compiler.showunchecked=false'
    '--java-options', '-Xmx512M'
    '--icon', (Join-Path $PSScriptRoot 'supergreenfoot.ico')
    '--runtime-image', $Runtime
)
if ($MakeMsi) {
    $jp += @(
        '--win-menu'
        '--win-menu-group', 'SuperGreenfoot'
        '--win-shortcut'
        '--win-dir-chooser'
        # Installs without administrator rights, which is what school machines need.
        '--win-per-user-install'
        '--win-upgrade-uuid', $UpgradeUuid
        '--file-associations', (Join-Path $PSScriptRoot 'assoc-greenfoot.properties')
        '--file-associations', (Join-Path $PSScriptRoot 'assoc-gfar.properties')
    )
}
& (Join-Path $JavaHome 'bin\jpackage.exe') @jp
if ($LASTEXITCODE -ne 0) { Die 'jpackage failed' }

if ($MakeMsi) {
    $msi = Get-ChildItem (Join-Path $Out 'work') -Filter '*.msi' | Select-Object -First 1
    if (-not $msi) { Die 'jpackage produced no MSI' }
    $final = Join-Path $Out "SuperGreenfoot-$Version.msi"
    Move-Item -Force $msi.FullName $final
    Write-Host ('Done: {0} ({1:N0} MB)' -f $final, ($msi.Length / 1MB))
}
else {
    $app = Join-Path $Out 'work\SuperGreenfoot'
    if (-not (Test-Path $app)) { Die 'jpackage produced no app folder' }
    $final = Join-Path $Out 'SuperGreenfoot'
    Move-Item -Force $app $final
    Write-Host "Done: $final (run $final\SuperGreenfoot.exe)"
}
Write-Host "Build stamp: $stamp"
