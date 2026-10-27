# Releasing

GitHub releases are the main way the app is published. Every published release gets a signed APK
from GitHub Actions, and every installed copy offers it through the in-app updater.

## One-time setup: the signing key

All releases must be signed with **the same key**, or Android refuses to install an update over
the old version. Create it once and keep a backup outside the repository (password manager);
losing it means users have to uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore myrsvp-release.jks -alias myrsvp \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store it as repository secrets (Settings → Secrets and variables → Actions), e.g. with `gh`:

```bash
base64 -i myrsvp-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS --body myrsvp
gh secret set RELEASE_KEY_PASSWORD
```

To build a signed release locally, create a git-ignored `keystore.properties` in the project root:

```properties
RELEASE_KEYSTORE_PATH=/path/to/myrsvp-release.jks
RELEASE_KEYSTORE_PASSWORD=…
RELEASE_KEY_ALIAS=myrsvp
RELEASE_KEY_PASSWORD=…
```

## Making a release

1. Make sure CI on `main` is green.
2. On GitHub: Releases → Draft a new release → tag `vX.Y.Z` (e.g. `v1.2.0`, always higher than the
   last one) → write the changelog as a Markdown list → **Publish**.
3. The *Release* workflow builds `myrsvp-X.Y.Z.apk` (version from the tag, versionCode
   X·1 000 000 + Y·1 000 + Z), runs the unit tests and attaches the APK to the release
   (a few minutes).
4. Installed apps find it on their next automatic check (at most every 6 hours) or via
   Settings → Check for updates, show the changelog and offer to install it.

Drafts and pre-releases are ignored by the updater, so a release can be tested before it is
offered to everyone: publish it as a pre-release, download the APK by hand, then mark it as the
latest release.

## Checklist per release

- [ ] Works with D-pad only AND touch only; tested on the Qin F22 Pro, a normal phone and the 640×960 emulator
- [ ] The update from the previous release installs over it and keeps books, positions and settings
- [ ] About & licenses lists every new library or font
- [ ] Changelog written for readers, not developers

## Google Play (later)

Play doesn't allow apps to update themselves, so a Play version would need a build without the
updater (and without the INTERNET permission), e.g. as a product flavor. Notes for then:

- Publisher: TH-Operations GmbH (organization account, D-U-N-S number, $25 one-time fee)
- applicationId `de.dahunni.myrsvp` can never change after the first upload
- Play App Signing re-signs the app with Google's key, so Play and GitHub installs can't update each other
- Data safety: "no data collected or shared"; privacy policy URL; content rating; Books & Reference
- App icon 512 × 512, feature graphic 1024 × 500, 2–8 screenshots, descriptions in English and German
