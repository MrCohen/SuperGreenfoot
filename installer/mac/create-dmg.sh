#!/bin/zsh
# Build a Finder-ready drag-to-Applications disk image from a staged .app.
# Usage: create-dmg.sh <app-path> <background-png> <output-dmg> <volume-name>
set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <app-path> <background-png> <output-dmg> <volume-name>" >&2
  exit 2
fi
APP="$1"
BACKGROUND="$2"
DMG="$3"
VOLUME_NAME="$4"
[[ -d "$APP" ]] || { echo "app missing: $APP" >&2; exit 2; }
[[ -f "$BACKGROUND" ]] || { echo "background missing: $BACKGROUND" >&2; exit 2; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/supergreenfoot-dmg.XXXXXX")"
MOUNT="$WORK/sgf-layout-$RANDOM"
RW_DMG="$WORK/layout.dmg"
MOUNTED=0
cleanup() {
  if [[ $MOUNTED -eq 1 ]]; then hdiutil detach "$MOUNT" -quiet || true; fi
  rm -rf "$WORK"
}
trap cleanup EXIT

# Leave room for HFS+ metadata, Finder's .DS_Store and the background image.
APP_KB="$(du -sk "$APP" | awk '{print $1}')"
SIZE_KB=$((APP_KB + 102400))
hdiutil create -quiet -size "${SIZE_KB}k" -fs HFS+ -volname "$VOLUME_NAME" "$RW_DMG"
mkdir "$MOUNT"
hdiutil attach -quiet -readwrite -noverify -noautoopen -mountpoint "$MOUNT" "$RW_DMG"
MOUNTED=1

ditto "$APP" "$MOUNT/SuperGreenfoot.app"
ln -s /Applications "$MOUNT/Applications"
mkdir "$MOUNT/.background"
cp "$BACKGROUND" "$MOUNT/.background/installer-background.png"
osascript "$SCRIPT_DIR/set-dmg-layout.applescript" "${MOUNT:t}" "$MOUNT/.background/installer-background.png"
sync
hdiutil detach "$MOUNT" -quiet
MOUNTED=0

mkdir -p "$(dirname "$DMG")"
rm -f "$DMG"
hdiutil convert -quiet "$RW_DMG" -format UDZO -imagekey zlib-level=9 -o "$DMG"
