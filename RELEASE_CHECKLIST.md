# GitHub Release Checklist

## One-time setup

1. Create a permanent signing key and keep at least two backups outside the repository:

   ```bash
   keytool -genkeypair -v -keystore release-key.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
   ```

2. On GitHub, open `Settings` → `Environments` → `New environment`, create an environment named `release`, and optionally require manual approval.
3. Add four secrets to the `release` environment:

   - `ANDROID_KEYSTORE_BASE64` — the contents of `release-key.jks` encoded as a single-line Base64 string;
   - `ANDROID_KEYSTORE_PASSWORD` — the keystore password;
   - `ANDROID_KEY_ALIAS` — the signing key alias;
   - `ANDROID_KEY_PASSWORD` — the signing key password.

   Linux:

   ```bash
   base64 -w 0 release-key.jks
   ```

   macOS:

   ```bash
   base64 < release-key.jks | tr -d '\n'
   ```

   PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release-key.jks"))
   ```

Never add the signing key or its Base64 representation to Git, issues, logs, or workflow files.

## Before building

- [ ] The working tree is clean and contains no `local.properties`, keystore, `keystore.properties`, cookies, tokens, or logs with personal data.
- [ ] `versionCode` has been incremented and `versionName` matches the upcoming tag.
- [ ] The pinned `yt-dlp` version and SHA-256 checksum in `app/build.gradle.kts` have been verified.
- [ ] `CHANGELOG.md` contains the upcoming version and correct date.
- [ ] `./gradlew clean check lintDebug assembleDebug assembleDebugAndroidTest` completes successfully.
- [ ] The permanent release keystore is configured through the local `keystore.properties` file.

## Release build

```bash
./gradlew assembleRelease bundleRelease -PreleaseAbiSplits=true
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-universal-release.apk
```

- [ ] The certificate matches the previous public release and is not a debug certificate.
- [ ] The APK installs on a clean device.
- [ ] The APK updates the previous public version without a signing error.
- [ ] Link analysis and actual audio/video downloads have been tested on a device.

## Source code and licenses

- [ ] A signed or annotated tag such as `v0.1.3` points to the exact source commit used to build the APK.
- [ ] GitHub's automatically generated source archives for the tag are available in the release.
- [ ] Source archives for `youtubedl-android 0.18.1` and `yt-dlp 2026.07.04`, or verified mirrors, are attached next to the APK files.
- [ ] The release description links to `THIRD_PARTY_NOTICES.md`.
- [ ] The APK contains `assets/legal/LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md`.

## Publishing

Recommended procedure:

```bash
git status
git add .
git commit -m "Prepare v0.1.3 release"
git push origin main
git tag -a v0.1.3 -m "YT-DW v0.1.3"
git push origin v0.1.3
```

The tag starts `.github/workflows/release.yml`. The workflow:

1. verifies that the tag matches `versionName`;
2. runs tests and lint;
3. builds signed `v8-lite`, `v7-legacy`, `x86-emulator`, and `universal` APKs plus an AAB;
4. verifies the APK certificates;
5. attaches third-party source archives and `SHA256SUMS.txt`;
6. creates a draft GitHub Release.

After the workflow succeeds, open the draft release, download the `v8-lite` APK, verify its SHA-256 again, and install it on a phone. Click **Publish release** only after this smoke test passes.

To rebuild an existing draft, open `Actions` → `Android Release` → `Run workflow`, enter its tag, and start the workflow. Old ABI-based asset names are removed, and the files and checksums are replaced.

- [ ] A SHA-256 checksum is published for every APK.
- [ ] The release text states: “The software is provided as is, without warranty. Users are responsible for complying with service rules, copyright law, and local laws.”
- [ ] The keystore and its backup are stored outside GitHub.

Do not promise complete protection from legal claims. The GPL limits warranties and liability only to the extent permitted by applicable law; it does not override copyright in downloaded material, website terms, patents, or country-specific requirements.
