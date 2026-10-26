#!/bin/zsh
# Build the SuperGreenfoot "Windows Standalone" package ON THE MAC: a folder
# that runs straight from a USB key or any writable folder on a Windows PC,
# with no installer and no admin rights. It is the equivalent of upstream's
# `windows-standalone` Ant target (Greenfoot-windows-<ver>.zip), which needs a
# Windows machine; this one cross-builds everything.
#
# Usage:
#   installer/windows/build-standalone.sh [--out <dir>] [--no-build] [--full-jdk]
#                                         [--install <folder>]
#
#   --install <folder>  also copy the result into <folder> (for example a USB
#                       key: /Volumes/USB\ DISK). Only SuperGreenfoot.exe and
#                       SuperGreenfoot\ are replaced; anything else there, such
#                       as a stock Greenfoot standalone, is left alone.
#   --full-jdk          ship the whole Windows JDK instead of a jlinked runtime.
#
# Requires: JDK 21 at $JAVA_HOME (for Gradle), mingw-w64 (brew install
# mingw-w64), curl, and network access the first time. Downloads are cached in
# ${SGF_WIN_CACHE:-~/Library/Caches/SuperGreenfoot/windows}:
#   - Temurin JDK 21 for Windows x64 (the runtime students' code compiles with)
#   - the same Temurin release for this Mac, only for its jlink (jlink must be
#     exactly the target's version)
#   - the Windows JavaFX jars matching the version in bluej/build.gradle
#
# Result: <out>/SuperGreenfoot-windows-standalone-<version>.zip containing
#
#   SuperGreenfoot.exe            USB launcher: starts SuperGreenfoot\SuperGreenfoot.exe
#                                 with the key's root as working directory
#   SuperGreenfoot\
#     SuperGreenfoot.exe          the normal launcher (upstream gflaunch, rebranded)
#     Start SuperGreenfoot.cmd    fallback where unknown .exe files are blocked
#     jdk\                        private Java runtime
#     lib\                        IDE jars, greenfoot.defs, docs
#
# Preferences go to <root>\userhome\supergreenfoot, beside a stock Greenfoot
# standalone's userhome\greenfoot, so the two never share settings.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LIB="$ROOT/greenfoot/build/resources/main/lib"
WINPKG="$ROOT/bluej/package"
OUT="$ROOT/build/installer-windows-standalone"
CACHE="${SGF_WIN_CACHE:-$HOME/Library/Caches/SuperGreenfoot/windows}"
BUILD=1
FULL_JDK=0
INSTALL=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) OUT="$2"; shift 2;;
    --no-build) BUILD=0; shift;;
    --full-jdk) FULL_JDK=1; shift;;
    --install) INSTALL="$2"; shift 2;;
    *) echo "unknown option $1"; exit 2;;
  esac
done
: "${JAVA_HOME:?set JAVA_HOME to a JDK 21}"
for t in x86_64-w64-mingw32-g++ x86_64-w64-mingw32-windres x86_64-w64-mingw32-strip; do
  command -v $t >/dev/null || { echo "$t not found: brew install mingw-w64"; exit 2; }
done
if [[ -n "$INSTALL" && ! -d "$INSTALL" ]]; then echo "--install folder $INSTALL does not exist"; exit 2; fi

# ---- 0. jars match the source (same guard as installer/mac/build-dmg.sh) ----
if [[ $BUILD -eq 1 ]]; then
  echo "Building (gradle :greenfoot:assemble :greenfoot:userJavadoc)..."
  ( cd "$ROOT" && ./gradlew :greenfoot:assemble :greenfoot:userJavadoc -q ) \
    || { echo "Gradle build failed; not packaging stale jars"; exit 1; }
fi
[[ -f "$LIB/boot.jar" ]] || { echo "run ./gradlew :greenfoot:assemble first ($LIB/boot.jar missing)"; exit 2; }
NEWEST_JAR=$(ls -t "$LIB"/*.jar | head -1)
NEWEST_SRC=$(find "$ROOT/greenfoot/src/main" "$ROOT/bluej/src/main" -type f -newer "$NEWEST_JAR" | head -1)
if [[ -n "$NEWEST_SRC" ]]; then
  echo "Source newer than $NEWEST_JAR (e.g. $NEWEST_SRC); rebuild first"; exit 1
fi
GIT_REV=$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo unknown)
if [[ -n "$(cd "$ROOT" && git status --porcelain --untracked-files=no 2>/dev/null)" ]]; then GIT_REV="$GIT_REV-dirty"; fi

VERSION=$(sed -n 's/^supergreenfoot_version=//p' "$ROOT/version.properties")
FX_VERSION=$(sed -n 's/^ *version = "\([0-9.]*\)".*javafx_version_setting.*/\1/p' "$ROOT/bluej/build.gradle")
[[ -n "$VERSION" && -n "$FX_VERSION" ]] || { echo "could not read versions (got '$VERSION', JavaFX '$FX_VERSION')"; exit 1; }
# Four numeric parts for the Windows version resource: 0.1.4 -> 0,1,4,0
VERSION_PARTS=(${(s:.:)${VERSION%%[^0-9.]*}} 0 0 0 0)
WIN_VERSION="${VERSION_PARTS[1]},${VERSION_PARTS[2]},${VERSION_PARTS[3]},${VERSION_PARTS[4]}"

rm -rf "$OUT"; mkdir -p "$OUT/work" "$CACHE"
OUT="$(cd "$OUT" && pwd)"
STAGE="$OUT/work/stage"
APPDIR="$STAGE/SuperGreenfoot"
mkdir -p "$APPDIR/lib"
echo "SuperGreenfoot $VERSION for Windows (JavaFX $FX_VERSION) -> $OUT"

# ---- 1. downloads (cached) ----
fetch() {  # fetch <url> <cache file name>
  local dest="$CACHE/$2"
  if [[ ! -s "$dest" ]]; then
    echo "  downloading $2..."
    curl -fsSL --retry 3 -o "$dest.part" "$1" && mv "$dest.part" "$dest"
  fi
}
ADOPTIUM=https://api.adoptium.net/v3
# Pin the Windows JDK release once, so the Mac jlink below can match it exactly.
JDK_RELEASE_FILE="$CACHE/jdk-release.txt"
if [[ ! -s "$JDK_RELEASE_FILE" || -n "${SGF_WIN_JDK_REFRESH:-}" ]]; then
  curl -fsS "$ADOPTIUM/assets/latest/21/hotspot?os=windows&architecture=x64&image_type=jdk&vendor=eclipse" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["release_name"])' > "$JDK_RELEASE_FILE"
fi
JDK_RELEASE=$(<"$JDK_RELEASE_FILE")   # e.g. jdk-21.0.12.1+1
echo "Windows JDK: $JDK_RELEASE"
fetch "$ADOPTIUM/binary/version/${JDK_RELEASE//+/%2B}/windows/x64/jdk/hotspot/normal/eclipse" "$JDK_RELEASE-windows-x64.zip"
WINJDK="$CACHE/$JDK_RELEASE-windows-x64"
if [[ ! -x "$WINJDK/bin/java.exe" && ! -f "$WINJDK/bin/java.exe" ]]; then
  rm -rf "$WINJDK" "$WINJDK.tmp"; mkdir -p "$WINJDK.tmp"
  unzip -q "$CACHE/$JDK_RELEASE-windows-x64.zip" -d "$WINJDK.tmp"
  mv "$WINJDK.tmp"/*/ "$WINJDK"; rm -rf "$WINJDK.tmp"
fi

for m in base controls graphics fxml media swing web; do
  fetch "https://repo1.maven.org/maven2/org/openjfx/javafx-$m/$FX_VERSION/javafx-$m-$FX_VERSION-win.jar" "javafx-$m-$FX_VERSION-win.jar"
done

# ---- 2. Java runtime ----
if [[ $FULL_JDK -eq 1 ]]; then
  echo "Copying the full Windows JDK..."
  rsync -a --exclude 'lib/src.zip' "$WINJDK/" "$APPDIR/jdk/"
else
  HOSTARCH=$([[ "$(uname -m)" == arm64 ]] && echo aarch64 || echo x64)
  fetch "$ADOPTIUM/binary/version/${JDK_RELEASE//+/%2B}/mac/$HOSTARCH/jdk/hotspot/normal/eclipse" "$JDK_RELEASE-mac-$HOSTARCH.tar.gz"
  HOSTJDK="$CACHE/$JDK_RELEASE-mac-$HOSTARCH"
  if [[ ! -x "$HOSTJDK/Contents/Home/bin/jlink" ]]; then
    rm -rf "$HOSTJDK" "$HOSTJDK.tmp"; mkdir -p "$HOSTJDK.tmp"
    tar -xzf "$CACHE/$JDK_RELEASE-mac-$HOSTARCH.tar.gz" -C "$HOSTJDK.tmp"
    mv "$HOSTJDK.tmp"/*/ "$HOSTJDK"; rm -rf "$HOSTJDK.tmp"
  fi
  echo "Linking the Windows runtime (all JDK modules, like the Mac app)..."
  # All modules: javac for students' code, jpackage/jlink for native game export.
  "$HOSTJDK/Contents/Home/bin/jlink" --module-path "$WINJDK/jmods" --add-modules ALL-MODULE-PATH \
      --output "$APPDIR/jdk" --strip-debug --no-man-pages --compress zip-6
  # The launcher's JNI mode and the IDE's JDK check look for these; jlink drops
  # neither, but say so loudly if that ever changes.
  [[ -f "$APPDIR/jdk/bin/javaw.exe" && -f "$APPDIR/jdk/bin/server/jvm.dll" ]] \
    || { echo "linked runtime lacks javaw.exe or jvm.dll"; exit 1; }
fi
du -sh "$APPDIR/jdk" | sed 's/^/  runtime size: /'

# ---- 3. application files (lib\ like upstream; same content as the Mac app) ----
echo "Staging application files..."
rsync -a --exclude 'testlib' --exclude 'javafx-*.jar' "$LIB/" "$APPDIR/lib/"
cp "$CACHE"/javafx-*-"$FX_VERSION"-win.jar "$APPDIR/lib/"
if [[ -d "$ROOT/bluej/doc/API" ]]; then mkdir -p "$APPDIR/lib/doc"; rsync -a "$ROOT/bluej/doc/API" "$APPDIR/lib/doc/"; fi
mkdir -p "$APPDIR/lib/greenfoot/common" && rsync -a "$ROOT/greenfoot/common/" "$APPDIR/lib/greenfoot/common/"
mkdir -p "$APPDIR/lib/doc" && cp "$ROOT/greenfoot/doc/LICENSE.txt" "$ROOT/greenfoot/doc/THIRDPARTYLICENSE.txt" "$ROOT/greenfoot/doc/GREENFOOT_LICENSES.txt" "$APPDIR/lib/doc/" 2>/dev/null || true
cp "$ROOT/greenfoot/doc/Greenfoot-README.txt" "$APPDIR/README.TXT" 2>/dev/null || true
printf 'SuperGreenfoot %s (Windows standalone, %s) built %s from %s\r\n' \
  "$VERSION" "$JDK_RELEASE" "$(date -u +%Y-%m-%dT%H:%MZ)" "$GIT_REV" > "$APPDIR/lib/supergreenfoot-build.txt"

# Point the launcher at the bundled runtime and keep preferences on the key
# (the same two edits upstream's windows-standalone target makes).
DEFS="$APPDIR/lib/greenfoot.defs"
grep -q '^#greenfoot\.windows\.vm=' "$DEFS" && grep -q '^#bluej\.userHome=' "$DEFS" \
  || { echo "greenfoot.defs no longer has the lines this script edits"; exit 1; }
sed -i '' -e 's/^#greenfoot\.windows\.vm=.*/greenfoot.windows.vm=jdk/' \
          -e 's/^#bluej\.userHome=.*/bluej.userHome=userhome/' "$DEFS"

# ---- 4. launchers, cross-compiled from upstream's C++ sources ----
echo "Compiling launchers (mingw-w64)..."
LW="$OUT/work/launch"; mkdir -p "$LW"
cp "$WINPKG"/winlaunch/* "$LW/"
cp "$WINPKG"/winusblaunch/usblaunch.cc "$LW/"
# Branded version resource and manifest; the upstream files are not modified.
cat > "$LW/greenfoot-version.rc" <<EOF
FILEVERSION $WIN_VERSION
PRODUCTVERSION $WIN_VERSION
BEGIN
	BLOCK "StringFileInfo"
	BEGIN
		BLOCK "04091200"
		BEGIN
			VALUE "CompanyName", "SuperGreenfoot contributors"
			VALUE "FileDescription", "SuperGreenfoot Launcher"
			VALUE "FileVersion", "$VERSION"
			VALUE "InternalName", "SuperGreenfoot"
			VALUE "OriginalFilename", "SuperGreenfoot.exe"
			VALUE "ProductName", "SuperGreenfoot"
			VALUE "ProductVersion", "$VERSION"
		END
	END
	BLOCK "VarFileInfo"
	BEGIN
		VALUE "Translation", 0x409, 1200
	END
END
EOF
# Anchor on assemblyIdentity: a bare version="..." also matches the <?xml?>
# declaration, and Windows refuses an exe whose manifest is not XML 1.0
# ("side-by-side configuration is incorrect").
sed -E -e "s/(<assemblyIdentity version=)\"[0-9.]+\"/\\1\"${WIN_VERSION//,/.}\"/" \
       -e 's/processorArchitecture="X86"/processorArchitecture="amd64"/' \
       -e 's/name="Greenfoot"/name="SuperGreenfoot"/' \
       -e 's#<description>Greenfoot</description>#<description>SuperGreenfoot</description>#' \
       "$WINPKG/winlaunch/gfmanifest.xml" > "$LW/gfmanifest.xml"
sed -e 's#"../winlaunch/#"#' "$WINPKG/winusblaunch/usblaunch.rc" \
  | sed -E -e 's/"(BlueJ\/Greenfoot USB Launcher)"/"SuperGreenfoot USB Launcher"/' \
           -e "s/(FILE|PRODUCT)VERSION 1,0,0,0/\\1VERSION $WIN_VERSION/" \
           -e "s/\"1\\.0\\.0\"/\"$VERSION\"/" \
           -e 's/"bluejusb(\.exe)?"/"SuperGreenfoot\1"/' \
           -e 's/"BlueJ group"/"SuperGreenfoot contributors"/' > "$LW/usblaunch.rc"
sed -E -e 's/processorArchitecture="X86"/processorArchitecture="amd64"/' \
  "$WINPKG/winusblaunch/usbmanifest.xml" > "$LW/usbmanifest.xml"
for m in "$LW/gfmanifest.xml" "$LW/usbmanifest.xml"; do
  xmllint --noout "$m" && head -1 "$m" | grep -q '^<?xml version="1.0"' \
    && grep -q "processorArchitecture=\"amd64\"" "$m" \
    || { echo "generated manifest $m is not valid; Windows would refuse the exe"; exit 1; }
done
grep -q "<assemblyIdentity version=\"${WIN_VERSION//,/.}\"" "$LW/gfmanifest.xml" \
  || { echo "version not stamped into gfmanifest.xml"; exit 1; }
(
  cd "$LW"
  CXX=x86_64-w64-mingw32-g++; RC=x86_64-w64-mingw32-windres; JNI=(-I"$WINJDK/include" -I"$WINJDK/include/win32")
  NAME='-DAPPNAME="SuperGreenfoot"'
  $RC -DGREENFOOT bjlaunch.rc resources.o
  $CXX -DGREENFOOT $NAME -DREQUIREDJAVA='"21"' -Os -Wall "${JNI[@]}" -c bjlaunch.cc -o bjlaunch.o
  $CXX -DGREENFOOT $NAME -Os -Wall -c javaprops.cc -o javaprops.o
  $CXX $NAME -Os -c javatest.cc -o javatest.o
  $CXX -static -mwindows -o SuperGreenfoot.exe resources.o bjlaunch.o javatest.o javaprops.o -lversion -lshlwapi
  $RC -DGREENFOOT usblaunch.rc usbresources.o
  $CXX $NAME -DEXENAME='"SuperGreenfoot.exe"' -Os "${JNI[@]}" -c usblaunch.cc -o usblaunch.o
  $CXX -static -mwindows -o SuperGreenfootUSB.exe usbresources.o usblaunch.o -lversion
  x86_64-w64-mingw32-strip SuperGreenfoot.exe SuperGreenfootUSB.exe
) > "$OUT/work/launch.log" 2>&1 || { cat "$OUT/work/launch.log"; echo "launcher build failed"; exit 1; }
cp "$LW/SuperGreenfoot.exe" "$APPDIR/SuperGreenfoot.exe"
cp "$LW/SuperGreenfootUSB.exe" "$STAGE/SuperGreenfoot.exe"

# Fallback for PCs whose policy blocks unrecognised .exe files but allows scripts.
cat > "$APPDIR/Start SuperGreenfoot.cmd" <<'EOF'
@echo off
rem Starts SuperGreenfoot without the .exe launcher. Settings are kept in
rem ..\userhome, like the launcher does when started from the key's root.
cd /d "%~dp0.."
start "" "%~dp0jdk\bin\javaw.exe" -Xmx512M -cp "%~dp0lib\boot.jar" bluej.Boot -greenfoot=true -bluej.compiler.showunchecked=false
EOF

# For a PC where the IDE starts but never shows its window: run it with a
# console, then save a thread dump of every Java process onto the key.
cat > "$APPDIR/Diagnose SuperGreenfoot.cmd" <<'EOF'
@echo off
rem Starts SuperGreenfoot with a console, waits, then saves a thread dump of
rem every running Java program (the IDE and its debug VM) for a developer.
rem Everything goes to ..\userhome\supergreenfoot on the key.
setlocal
cd /d "%~dp0.."
set "OUT=%CD%\userhome\supergreenfoot"
if not exist "%OUT%" mkdir "%OUT%"
set "JDK=%~dp0jdk\bin"
echo Starting SuperGreenfoot. Leave this window open for about a minute...
start "SuperGreenfoot" /b "%JDK%\java.exe" -Xmx512M -cp "%~dp0lib\boot.jar" bluej.Boot -greenfoot=true -bluej.compiler.showunchecked=false > "%OUT%\console.txt" 2>&1
timeout /t 45 /nobreak >nul
echo Saving diagnostics...
"%JDK%\jcmd.exe" -l > "%OUT%\java-processes.txt" 2>&1
type nul > "%OUT%\threads.txt"
for /f "tokens=1" %%p in ('"%JDK%\jcmd.exe" -l') do (
  echo ===== process %%p >> "%OUT%\threads.txt"
  "%JDK%\jcmd.exe" %%p Thread.print -l >> "%OUT%\threads.txt" 2>&1
)
tasklist /v /fi "imagename eq java.exe" > "%OUT%\windows.txt" 2>&1
echo.
echo Done. The files are in %OUT%
echo (console.txt, threads.txt, java-processes.txt, windows.txt).
echo You can close SuperGreenfoot now.
pause
EOF
sed -i '' 's/$/\r/' "$APPDIR/Start SuperGreenfoot.cmd" "$APPDIR/Diagnose SuperGreenfoot.cmd"

# ---- 5. zip, and optionally install beside whatever is already there ----
ZIP="$OUT/SuperGreenfoot-windows-standalone-$VERSION.zip"
echo "Zipping..."
( cd "$STAGE" && zip -qr -X "$ZIP" SuperGreenfoot.exe SuperGreenfoot )
ls -la "$ZIP"

if [[ -n "$INSTALL" ]]; then
  echo "Installing into $INSTALL..."
  # FAT/exFAT keys cannot hold Unix permissions or owners, and macOS stores any
  # extended attribute there as a visible "._name" file: copy none, sweep up any.
  # (jlink writes read-only legal files, and xattr needs them writable.)
  chmod -R u+w "$STAGE" && xattr -cr "$STAGE"
  # Remove only our own files; never touch a stock Greenfoot, BlueJ or userhome.
  rm -rf "$INSTALL/SuperGreenfoot" "$INSTALL/SuperGreenfoot.exe"
  rsync -rt --modify-window=2 "$STAGE/" "$INSTALL/"
  dot_clean -m "$INSTALL/SuperGreenfoot"
  rm -f "$INSTALL/._SuperGreenfoot" "$INSTALL/._SuperGreenfoot.exe"
  cat "$INSTALL/SuperGreenfoot/lib/supergreenfoot-build.txt"
fi
echo "Done: $ZIP"
