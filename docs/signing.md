# Code signing: macOS and Windows

Decision D9: installers and exported games must be signed. This document is
the setup guide. Prices and vendor policies change; verify before buying.

## macOS (Apple Developer account exists)

State found on the dev Mac on 2026-09-16: Xcode is installed, `notarytool`
works, and the keychain holds three **"Apple Development"** identities for
team `QTS5DK2S8Q` (two of them revoked). There is **no "Developer ID
Application"** identity yet. Apple Development certificates only sign builds
for your own devices; distributing outside the App Store needs Developer ID.
Note: the Team ID is the certificate's **OU** field (`QTS5DK2S8Q`). The ID in
parentheses in the certificate name (`T9PN8DQTUL`) is the personal developer
ID and is *not* accepted by `notarytool --team-id`.

### One-time setup (about 20 minutes)

1. **Create the Developer ID Application certificate.** Easiest route: Xcode
   > Settings > Accounts > select the Apple ID > Manage Certificates > "+" >
   *Developer ID Application*. Xcode generates the key pair and installs the
   certificate into the login keychain. (Alternative: create a CSR in
   Keychain Access and upload it at developer.apple.com > Certificates.)
   Only the account holder can create Developer ID certificates.
2. Optionally also create *Developer ID Installer* if we ever ship `.pkg`.
   `jpackage --type dmg` does not need it, so skip for now.
3. **Verify:**
   ```sh
   security find-identity -v -p codesigning | grep "Developer ID Application"
   ```
4. **App-specific password for notarization.** At appleid.apple.com >
   Sign-In and Security > App-Specific Passwords, create one named
   "SuperGreenfoot notarytool". Then store it once in the keychain:
   ```sh
   xcrun notarytool store-credentials "SuperGreenfoot" \
     --apple-id jord81@gmail.com --team-id QTS5DK2S8Q
   ```
   (paste the app-specific password when prompted). CI will instead use
   `--apple-id/--team-id/--password` from GitHub secrets.
5. Tidy: the two revoked "Apple Development" certificates can be deleted
   from Keychain Access; they are harmless but confuse tooling.

### How builds will use it

- IDE installer: `jpackage --type dmg --mac-sign --mac-signing-key-user-name
  "Developer ID Application: <name> (QTS5DK2S8Q)"` with a hardened-runtime
  entitlements file (JIT + unsigned memory for the JVM), then
  `xcrun notarytool submit <dmg> --keychain-profile SuperGreenfoot --wait`
  and `xcrun stapler staple <dmg>`.
- Exported games: the same flow, driven from the IDE's Export dialog, using
  the teacher's identity configured once in Preferences. Notarization takes
  1-10 minutes per submission; the dialog shows progress.
- CI (GitHub Actions macOS runner): import a `.p12` export of the Developer
  ID certificate from a secret, sign, notarize, staple.

## Windows (no certificate yet)

Since mid-2023, standard code-signing certificates must live on a FIPS
hardware token or in a cloud signing service, so "download a .pfx" no
longer exists for new certificates. The realistic options for a solo
open-source developer:

| Option | Cost (verify) | Fit | Notes |
|---|---|---|---|
| **Azure Trusted Signing (recommended)** | about US$10/month, Basic tier | Best for CI | Microsoft-run cloud signing; individuals are eligible after identity verification (organisations may need 3 years of history). Signs with SignTool/`azure/trusted-signing-action` in GitHub Actions. Certificates are short-lived and rotated automatically. SmartScreen reputation still has to build up over downloads. |
| **SignPath Foundation** | Free for open-source | Good if approved | Free signing service for OSS projects; the certificate names "SignPath Foundation" plus the project. Signing happens inside their CI integration and they review the project first. GPL project qualifies. |
| **Certum Open Source Code Signing** | about EUR 70-90/year plus a card reader once | OK | Aimed at OSS authors; cheapest owned certificate. Uses a smart card or their SimplySign cloud app; CI use is awkward. |
| Traditional OV certificate (SSL.com, Sectigo, DigiCert, GlobalSign) | US$200-500/year plus token | Fine, pricier | OV gives no instant SmartScreen trust either; EV (US$300-700/year) does, and is the only way to skip the reputation period. |

Recommendation: apply for **Azure Trusted Signing** now (needs an Azure
account with a payment method and identity validation, typically 1-7 days),
and apply to **SignPath Foundation** in parallel as a free fallback. Either
way, Windows builds happen on a GitHub Actions `windows-latest` runner, so
the certificate never has to live on your Mac.

What signing covers on Windows: the IDE installer (`.msi` from
`jpackage`/WiX), the exported game `.exe`/`.msi`, and the launcher inside.
Unsigned exported games will show the SmartScreen "unrecognized app" panel;
signed OV/Trusted Signing builds show it less often and lose it as
downloads accumulate.

## Timeline in the plan

- Phase 0 (now): create the Developer ID certificate, store notarytool
  credentials, start the Windows application.
- Phase 3: first `jpackage` bundles, signed on macOS locally.
- Phase 4: CI signing for both platforms; exported-game signing from the IDE.

## How the export uses it (implemented in Phase 4)

Share > Application > "Also build a native app": choose DMG, pick the
Developer ID identity from the list (auto-detected from the keychain) and enter
the notarytool profile name ("SuperGreenfoot" if you followed the steps above).
The IDE runs, in order: `jpackage --type dmg --mac-sign --mac-signing-key-user-name
"<name (TEAM)>" ...`, `xcrun notarytool submit <dmg> --keychain-profile <profile> --wait`,
`xcrun stapler staple <dmg>`. Tool output is in the BlueJ debug log.

Verify a result by hand:

```sh
codesign --verify --deep --strict --verbose=2 "My Game.app"
spctl --assess --type execute --verbose "My Game.app"      # accepted only when notarized
```
