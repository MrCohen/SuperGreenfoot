# Phase 4: native packages, signing, notarization

## What the Application export tab does now

1. Builds the executable jar (Phase 3).
2. Optionally (**Also build a native app for this computer**) runs the JDK's
   `jpackage` on it, producing an app that needs no Java installed:
   - macOS: `<name>.app` (about 80 MB, 2 seconds) or a `.dmg`.
   - Windows: an app folder called `<name> app`, or an `.msi` installer. The
     tab offers the `.msi` only where the WiX 3 toolset is installed, and
     still falls back to the folder if the `.msi` build fails.
   - Linux: an app folder called `<name> app`.
3. On macOS, if a **Developer ID Application** certificate is in the keychain,
   (the packager first signs a scratch file so the keychain prompt appears once;
   answer it with Always Allow)
   the tab offers to sign with it (`jpackage --mac-sign`, hardened runtime with
   the JIT entitlements Java needs) and to **notarize** with a `notarytool`
   keychain profile, then staples the ticket. The DMG file itself is signed too, so Gatekeeper
   reports "Notarized Developer ID" for the image, not just the app inside. Notarization takes a few
   minutes; progress shows in the dialog and the tool output goes to the
   BlueJ debug log.

An earlier export of the same name at the same place is replaced: the jar is
overwritten, and a previous `<name>.app` or `<name> app` is deleted before
jpackage runs, since jpackage refuses to write over an existing folder. It is
deleted only if it looks like something jpackage built (`Contents` on macOS,
`app` and `runtime` elsewhere); any other folder in the way is left alone and
the export stops with a message naming it. The app is built in a staging
folder first and then moved into place.

The " app" suffix exists because of a bug in 0.2.0: on Windows and Linux the
app folder was named exactly like the scenario, the export destination
defaults to the folder that holds the scenario, so the "previous export"
deleted before jpackage ran was the scenario itself. Fixed in 0.2.1.

Choices (native on/off, identity, profile) are remembered in the IDE
preferences. Native apps only run on the kind of computer that built them:
build on a Mac for Macs and on Windows for Windows.

## Implementation

`greenfoot.export.NativePackager` (pure JDK, no UI) builds the `jpackage`
command line (`buildJpackageCommand`, unit-tested), runs it, locates the
artifact, and on macOS drives `ditto`/`xcrun notarytool submit --wait`/
`xcrun stapler staple`. `ExportInfo` carries the options; `Exporter.makeApplication`
calls the packager after the jar is written. Modules included in the private
runtime: `java.base, java.desktop, java.logging, java.prefs, java.xml,
java.compiler, java.management, jdk.jdi, jdk.xml.dom, jdk.unsupported` (from
`jdeps` on the runtime jar; the BlueJ classes reference the last few).

## The fork's own macOS installer

`installer/mac/build-dmg.sh [--sign "<name (TEAM)>"] [--notarize <profile>]` (needs
`JAVA_HOME` = full JDK 21+ and `./gradlew :greenfoot:assemble :greenfoot:userJavadoc`):

1. Stages the flattened `lib` directory (all jars, labels, defs, images, fonts),
   the API docs, `greenfoot/common`, licenses and README, exactly like upstream's
   `Contents/Java`.
2. `jlink`s a runtime with **all** JDK modules (so javac, JDI and everything the
   IDE touches is present). No `jmods` are shipped: the native libraries inside
   `.jmod` archives are unsigned and Apple's notary rejected the first attempt
   with 175 findings, all inside jmods. Instead the installed IDE exports native
   games with `jpackage --runtime-image <its own runtime>`
   (`NativePackager.findRuntimeImage`), which needs no jlink and yields a game of
   about 90 MB (versus 79 MB from a fresh jlink when running from a full JDK).
3. `jpackage --type app-image` with `bluej.Boot` as main class, `-greenfoot=true`,
   file associations for `.greenfoot` and `.gfar`, Greenfoot's icon, and, when
   signing, the hardened-runtime entitlements Java needs (JIT, unsigned memory,
   library validation off, audio input for the sound recorder). The JNA jar's
   native library is signed inside the jar first (notarization checks it).
4. `installer/mac/create-dmg.sh` builds the DMG with an Applications link,
   the [DrawSimple background](../branding/DMG_CREDITS.md), and Finder icon
   positions that show the drag-to-Applications step. The DMG is signed,
   notarized (`notarytool --wait`) and stapled.

The script runs `./gradlew :greenfoot:assemble :greenfoot:userJavadoc` first
(`--no-build` skips it; the Gradle task `packageSuperGreenfootMac` passes that
because it already depends on both), refuses to package when any file under
`greenfoot/src/main` or `bluej/src/main` is newer than the built
`greenfoot.jar`, and writes the git revision into
`Contents/app/supergreenfoot-build.txt`. Both guards exist because on
2026-09-16 an installer shipped with a committed but never-compiled export fix.

Sizes: about 160 MB app, 145 MB DMG. Build time about 20 s unsigned; a few
minutes signed (each library gets a timestamped signature) plus notarization.
Verified: the packaged IDE launches and opens scenarios, and its own runtime
builds a native game via `--runtime-image`.

## The fork's own Windows packages

Windows gets two unsigned packages; SmartScreen warns about both ("More
info", then "Run anyway") until a code-signing certificate exists.

- **The MSI installer**, `installer/windows/build-msi.ps1` (on Windows; the
  Gradle task is `packageSuperGreenfootWindows`, the `dev.ps1` command
  `msi`). It mirrors the macOS script: the same staged files and build stamp
  (`supergreenfoot-build.txt`), a jlinked runtime with every module and no
  jmods, then `jpackage --type msi`. The MSI installs per user (no
  administrator rights), carries a fixed upgrade UUID so a newer version
  replaces an older one, registers `.greenfoot` and `.gfar`, and adds a Start
  menu entry. It needs WiX 3.14 (JDK 21's jpackage cannot drive WiX 4 or 5),
  found on `PATH`, through `%WIX%`, or in the usual install folders; without
  it the script builds an app folder instead. The "Build SuperGreenfoot
  installers" workflow builds it on a GitHub runner on demand.
- **The standalone zip**, `installer/windows/build-standalone.sh` (cross-built
  on a Mac: `./dev winzip`, or `./dev usb <folder>` to copy it onto a USB
  key). It downloads a Windows JDK 21 and the Windows JavaFX jars, jlinks a
  runtime, and builds upstream's USB launchers renamed, with a
  `Start SuperGreenfoot.cmd` fallback for machines that block unknown `.exe`
  files and a `Diagnose SuperGreenfoot.cmd`. Nothing is installed; settings
  live in a `userhome` folder beside it. It builds into
  `build/installer-windows-standalone`, so it does not clear the MSI's
  `build/installer-windows`.

Windows refuses to install an MSI over the same version, so a test machine
must uninstall the earlier build first (Settings > Apps, or `msiexec /x` with
the *installed* product code: jpackage makes a new one for every build).

## macOS quirk: a double-clicked jar that "does nothing"

Finder opens `.jar` files through Apple's resident `JavaLauncher` process. If
an earlier launch of a jar at the same path failed (for example a broken
export), that JavaLauncher instance can keep ignoring re-opens of that path
while other jars still work, even after the file has been replaced with a
good one. Symptom: the Dock bounce, then nothing, with no Java process
started. Fix: quit `JavaLauncher` in Activity Monitor (or `pkill JavaLauncher`)
and double-click again. Running `java -jar Game.jar` in Terminal bypasses it.

## Where the exporter finds the runtime jar

`Exporter.findRuntimeJar` looks in `Config.getBlueJLibDir()` first
(`greenfoot/build/resources/main/lib` under Gradle, `Contents/app` in the
installed IDE, next to `boot.jar`) and then in the `greenfoot` subfolder. If
neither has `supergreenfoot-runtime.jar` the export stops with a visible
message instead of writing a jar without the engine.

## Requirement for the fork's own installer (background)

`jpackage` lives in a full JDK. Upstream's installer bundles a jlinked runtime
without it (the installed Greenfoot 3.9 has only `java` in its bundled JDK),
so the SuperGreenfoot installers bundle a runtime jlinked with **every**
module, `jdk.jpackage` and `jdk.jlink` included, and the native export works
from the installed IDE on macOS and Windows.

## World-size guidance (Phase 2c)

- The World class template now carries the recommended sizes as a comment
  above `super(600, 400, 1)`.
- The IDE full-screen view shows the current scale at the bottom left, e.g.
  `640x360 at 3x (pixel-perfect)` or `600x400 at 2.25x (not a whole number...)`.
