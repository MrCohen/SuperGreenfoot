#!/bin/zsh
# Build the SuperGreenfoot macOS app and DMG with jpackage, optionally signed
# with a Developer ID and notarized.
#
# Usage:
#   installer/mac/build-dmg.sh [--sign "<Developer ID name (TEAM)>"] [--notarize <keychain-profile>]
#                              [--out <dir>] [--full-jdk] [--no-build]
#
# Requires: a full JDK 21+ at $JAVA_HOME (jlink, jpackage, jmods). The script
# runs ./gradlew :greenfoot:assemble :greenfoot:userJavadoc itself so the
# staged jars always match the source tree; --no-build skips that (the Gradle
# task packageSuperGreenfootMac passes it because it already depends on both).
#
# Result: <out>/SuperGreenfoot-<version>.dmg containing SuperGreenfoot.app with
# a private runtime that also has jpackage + jmods, so the installed IDE can
# export native games.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LIB="$ROOT/greenfoot/build/resources/main/lib"
OUT="$ROOT/build/installer-mac"
SIGN=""
NOTARIZE=""
FULL_JDK=0
BUILD=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --sign) SIGN="$2"; shift 2;;
    --notarize) NOTARIZE="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --full-jdk) FULL_JDK=1; shift;;
    --no-build) BUILD=0; shift;;
    *) echo "unknown option $1"; exit 2;;
  esac
done
: "${JAVA_HOME:?set JAVA_HOME to a full JDK 21+}"
for t in jlink jpackage; do [[ -x "$JAVA_HOME/bin/$t" ]] || { echo "$JAVA_HOME has no $t"; exit 2; }; done
[[ -d "$JAVA_HOME/jmods" ]] || { echo "$JAVA_HOME has no jmods directory"; exit 2; }

# ---- 0. make sure the jars match the source (a stale build once shipped an
#         installer without a committed fix) ----
if [[ $BUILD -eq 1 ]]; then
  echo "Building (gradle :greenfoot:assemble :greenfoot:userJavadoc)..."
  ( cd "$ROOT" && ./gradlew :greenfoot:assemble :greenfoot:userJavadoc -q ) \
    || { echo "Gradle build failed; not packaging stale jars"; exit 1; }
fi
[[ -f "$LIB/boot.jar" ]] || { echo "run ./gradlew :greenfoot:assemble first ($LIB/boot.jar missing)"; exit 2; }
NEWEST_SRC=$(find "$ROOT/greenfoot/src/main" "$ROOT/bluej/src/main" -type f -newer "$LIB/greenfoot.jar" | head -1)
if [[ -n "$NEWEST_SRC" ]]; then
  echo "Source newer than $LIB/greenfoot.jar (e.g. $NEWEST_SRC); rebuild first"; exit 1
fi
GIT_REV=$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo unknown)
if [[ -n "$(cd "$ROOT" && git status --porcelain --untracked-files=no 2>/dev/null)" ]]; then GIT_REV="$GIT_REV-dirty"; fi

VERSION=$(python3 - "$ROOT/version.properties" <<'EOF'
import sys
p=dict(l.strip().split('=',1) for l in open(sys.argv[1]) if '=' in l and not l.startswith('#'))
print(p['greenfoot_major']+'.'+p['greenfoot_minor']+'.'+p['greenfoot_release']+p.get('greenfoot_suffix',''))
EOF
)
rm -rf "$OUT"; mkdir -p "$OUT/input" "$OUT/work"
OUT="$(cd "$OUT" && pwd)"   # absolute: later steps cd into subfolders
echo "SuperGreenfoot $VERSION -> $OUT"

# ---- 1. application files (the flattened lib dir, like upstream's Contents/Java) ----
echo "Staging application files..."
rsync -a --exclude 'testlib' "$LIB/" "$OUT/input/"
if [[ -d "$ROOT/bluej/doc/API" ]]; then mkdir -p "$OUT/input/doc"; rsync -a "$ROOT/bluej/doc/API" "$OUT/input/doc/"; fi
mkdir -p "$OUT/input/greenfoot/common" && rsync -a "$ROOT/greenfoot/common/" "$OUT/input/greenfoot/common/"
mkdir -p "$OUT/input/doc" && cp "$ROOT/greenfoot/doc/LICENSE.txt" "$ROOT/greenfoot/doc/THIRDPARTYLICENSE.txt" "$ROOT/greenfoot/doc/GREENFOOT_LICENSES.txt" "$OUT/input/doc/" 2>/dev/null || true
cp "$ROOT/greenfoot/doc/Greenfoot-README.txt" "$OUT/input/README.TXT" 2>/dev/null || true
printf 'SuperGreenfoot %s built %s from %s\n' "$VERSION" "$(date -u +%Y-%m-%dT%H:%MZ)" "$GIT_REV" > "$OUT/input/supergreenfoot-build.txt"

# The JNA jar carries an unsigned native library; notarization requires it signed.
if [[ -n "$SIGN" ]]; then
  JNA=$(ls "$OUT/input"/jna-*.jar | head -1)
  echo "Signing native library inside $(basename "$JNA")..."
  ( cd "$OUT/work" && rm -rf jna && mkdir jna && cd jna \
    && "$JAVA_HOME/bin/jar" xf "$JNA" com/sun/jna/darwin/libjnidispatch.jnilib \
    && codesign --timestamp --options=runtime -f -s "Developer ID Application: $SIGN" com/sun/jna/darwin/libjnidispatch.jnilib \
    && "$JAVA_HOME/bin/jar" uf "$JNA" com/sun/jna/darwin/libjnidispatch.jnilib )
fi

# ---- 2. runtime: jlink the modules the IDE needs, plus jpackage/jlink for exports ----
RUNTIME="$OUT/work/runtime"
if [[ $FULL_JDK -eq 1 ]]; then
  echo "Using the full JDK as runtime..."
  RUNTIME="$JAVA_HOME"
else
  echo "Linking runtime (all JDK modules, so nothing the IDE or javac needs is missing)..."
  "$JAVA_HOME/bin/jlink" --module-path "$JAVA_HOME/jmods" --add-modules ALL-MODULE-PATH --output "$RUNTIME" \
      --strip-debug --no-header-files --no-man-pages --compress zip-6
fi
# No jmods are shipped: the native libraries inside .jmod archives are unsigned
# and Apple's notary rejects them. The installed IDE builds game runtimes by
# copying its own (signed) runtime image instead (NativePackager.findRuntimeImage).
du -sh "$RUNTIME" | sed 's/^/  runtime size: /'

# ---- 3. jpackage ----
echo "Running jpackage..."
JP=("$JAVA_HOME/bin/jpackage"
  --type app-image
  --input "$OUT/input"
  --dest "$OUT/work"
  --name SuperGreenfoot
  --app-version "$VERSION"
  --vendor "SuperGreenfoot"
  --description "SuperGreenfoot: Greenfoot with precise actors, real sound, full screen and game export"
  --main-jar boot.jar
  --main-class bluej.Boot
  --arguments "-greenfoot=true"
  --arguments "-bluej.compiler.showunchecked=false"
  --java-options "-Dapple.laf.useScreenMenuBar=true"
  --java-options "-Xmx512M"
  --icon "$ROOT/greenfoot/resources/images/greenfoot.icns"
  --runtime-image "$RUNTIME"
  --mac-package-identifier org.supergreenfoot.SuperGreenfoot
  --mac-package-name SuperGreenfoot
  --file-associations "$ROOT/installer/mac/assoc-greenfoot.properties"
  --file-associations "$ROOT/installer/mac/assoc-gfar.properties"
)
if [[ -n "$SIGN" ]]; then
  JP+=(--mac-sign --mac-signing-key-user-name "$SIGN" --mac-entitlements "$ROOT/installer/mac/entitlements.plist")
fi
"${JP[@]}"
APP="$OUT/work/SuperGreenfoot.app"
[[ -d "$APP" ]] || { echo "jpackage produced no app"; exit 1; }
if [[ -n "$SIGN" ]]; then
  echo "Verifying app signature..."
  codesign --verify --deep --strict --verbose=2 "$APP"
fi

# ---- 4. DMG with the DrawSimple background and Finder icon layout ----
DMG="$OUT/SuperGreenfoot-$VERSION.dmg"
echo "Creating $DMG..."
"$ROOT/installer/mac/create-dmg.sh" "$APP" \
  "$ROOT/installer/mac/assets/dmg-background.png" "$DMG" "SuperGreenfoot $VERSION"
if [[ -n "$SIGN" ]]; then
  codesign --timestamp -f -s "Developer ID Application: $SIGN" "$DMG"
  if [[ -n "$NOTARIZE" ]]; then
    echo "Notarizing (a few minutes)..."
    xcrun notarytool submit "$DMG" --keychain-profile "$NOTARIZE" --wait
    xcrun stapler staple "$DMG"
    spctl --assess --type open --context context:primary-signature --verbose "$DMG" || true
  fi
fi
ls -la "$DMG"
echo "Done: $DMG"
