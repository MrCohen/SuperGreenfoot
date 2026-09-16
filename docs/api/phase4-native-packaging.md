# Phase 4: native packages, signing, notarization

## What the Application export tab does now

1. Builds the executable jar (Phase 3).
2. Optionally (**Also build a native app for this computer**) runs the JDK's
   `jpackage` on it, producing an app that needs no Java installed:
   - macOS: `.app` folder (about 80 MB, 2 seconds) or a `.dmg`.
   - Windows: app folder, or an `.msi` installer when WiX 3 is installed
     (falls back to the folder automatically if the `.msi` build fails).
   - Linux: app folder.
3. On macOS, if a **Developer ID Application** certificate is in the keychain,
   (the packager first signs a scratch file so the keychain prompt appears once;
   answer it with Always Allow)
   the tab offers to sign with it (`jpackage --mac-sign`, hardened runtime with
   the JIT entitlements Java needs) and to **notarize** with a `notarytool`
   keychain profile, then staples the ticket. The DMG file itself is signed too, so Gatekeeper
   reports "Notarized Developer ID" for the image, not just the app inside. Notarization takes a few
   minutes; progress shows in the dialog and the tool output goes to the
   BlueJ debug log.

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
   IDE touches is present) and copies the JDK's `jmods` into the app folder,
   because jpackage strips `jmods` from runtime images. The installed IDE passes
   `Contents/app/jmods` as `--module-path` when it exports a native game
   (`NativePackager.findModulePath`).
3. `jpackage --type app-image` with `bluej.Boot` as main class, `-greenfoot=true`,
   file associations for `.greenfoot` and `.gfar`, Greenfoot's icon, and, when
   signing, the hardened-runtime entitlements Java needs (JIT, unsigned memory,
   library validation off, audio input for the sound recorder). The JNA jar's
   native library is signed inside the jar first (notarization checks it).
4. `hdiutil` builds the DMG with an Applications link; the DMG is signed,
   notarized (`notarytool --wait`) and stapled.

Sizes: 237 MB app, 216 MB DMG (about 145 MB without jmods). Build time about
20 s unsigned. Verified: the packaged IDE launches and opens scenarios, and its
own runtime builds a native game from the shipped jmods.

## Requirement for the fork's own installer (background)

`jpackage` lives in a full JDK. Upstream's installer bundles a jlinked runtime
without it (the installed Greenfoot 3.9 has only `java` in its bundled JDK),
so the SuperGreenfoot installer must bundle a **full JDK** (or jlink with
`jdk.jpackage` and `jdk.jlink` included) for the native export to work from
the installed IDE. Until then it works when the IDE runs from Gradle.

## World-size guidance (Phase 2c)

- The World class template now carries the recommended sizes as a comment
  above `super(600, 400, 1)`.
- The IDE full-screen view shows the current scale at the bottom left, e.g.
  `640x360 at 3x (pixel-perfect)` or `600x400 at 2.25x (not a whole number...)`.
