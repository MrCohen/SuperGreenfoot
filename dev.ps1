<#
SuperGreenfoot developer helper for Windows: try changes without building an
installer. The Windows counterpart of ./dev (zsh, macOS only).

  .\dev.ps1 run                 start the IDE straight from the source tree (skips tests)
  .\dev.ps1 test                run the engine test suite
  .\dev.ps1 player <scenario>   run a scenario folder in the standalone player (add --fullscreen or --run)
  .\dev.ps1 app                 build an unsigned app folder and start it
  .\dev.ps1 msi                 build the unsigned MSI installer (needs WiX 3)

The IDE from "run" and "app" shares preferences with an installed
SuperGreenfoot; its log is greenfoot-debuglog.txt in the same folder. To test
in isolation, pass your own user home:
  .\dev.ps1 run -- -bluej.userHome=C:\temp\sgf-home

Set SGF_JAVA_HOME to use a different JDK 21. A full JDK (jmods + jpackage) is
needed for "app" and "msi"; WiX 3.14 is needed for "msi" and is picked up from
%LOCALAPPDATA%\Programs\WiX314 or the PATH.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)] [string] $Command = 'help',
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)] [string[]] $Rest
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$DefaultJdk = "$env:LOCALAPPDATA\Programs\Java\jdk-21.0.12.1+1"
$env:JAVA_HOME = if ($env:SGF_JAVA_HOME) { $env:SGF_JAVA_HOME } else { $DefaultJdk }
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Error "JDK 21 not found at $env:JAVA_HOME (install Temurin 21 'full', or set SGF_JAVA_HOME)"
    exit 2
}
$Wix = "$env:LOCALAPPDATA\Programs\WiX314"
if (Test-Path "$Wix\candle.exe") { $env:PATH = "$Wix;$env:PATH" }

$Runtime = 'greenfoot\build\resources\main\lib\supergreenfoot-runtime.jar'
$Gradle  = '.\gradlew.bat'

function Invoke-Checked([string] $What, [scriptblock] $Block) {
    & $Block
    if ($LASTEXITCODE -ne 0) { Write-Error "$What failed"; exit $LASTEXITCODE }
}

switch ($Command) {
    'run' {
        Write-Host 'Starting the IDE from source (tests skipped; .\dev.ps1 test runs them)...'
        $extra = if ($Rest) { '--args=' + ($Rest -join ' ') } else { $null }
        Invoke-Checked 'runGreenfoot' { & $Gradle runGreenfoot -x test -q @extra }
    }
    'test' {
        Invoke-Checked 'test' { & $Gradle ':greenfoot:test' }
    }
    'player' {
        if (-not $Rest -or -not (Test-Path $Rest[0] -PathType Container)) {
            Write-Error 'usage: .\dev.ps1 player <scenario folder> [--fullscreen] [--run]'
            exit 2
        }
        $scenario = (Resolve-Path $Rest[0]).Path
        $playerArgs = @($Rest | Select-Object -Skip 1)
        Invoke-Checked 'assemble' { & $Gradle ':greenfoot:assemble' -x test -q }
        Write-Host "Compiling $scenario..."
        $sources = (Get-ChildItem "$scenario\*.java").FullName
        & "$env:JAVA_HOME\bin\javac.exe" -nowarn -cp $Runtime -d $scenario @sources 2>&1 |
            Where-Object { $_ -notmatch 'unknown enum constant|class file for threadchecker|^\d+ warnings?$' }
        & "$env:JAVA_HOME\bin\java.exe" -cp $Runtime greenfoot.player.PlayerMain $scenario @playerArgs
    }
    'app' {
        Invoke-Checked 'build-msi.ps1 -AppFolder' {
            & "$PSScriptRoot\installer\windows\build-msi.ps1" -AppFolder -Out 'build\installer-windows-dev'
        }
        Write-Host 'Starting build\installer-windows-dev\SuperGreenfoot\SuperGreenfoot.exe (unsigned)...'
        Start-Process 'build\installer-windows-dev\SuperGreenfoot\SuperGreenfoot.exe'
    }
    'msi' {
        if (& git.exe status --porcelain --untracked-files=no) {
            Write-Host 'Note: uncommitted changes are included; the build stamp will say -dirty.'
        }
        Invoke-Checked 'build-msi.ps1' { & "$PSScriptRoot\installer\windows\build-msi.ps1" }
    }
    default {
        # Print this file's own header comment as the usage message.
        (Get-Content $PSCommandPath -Raw) -replace '(?s)^<#\s*(.*?)\s*#>.*', '$1' | Write-Host
    }
}
