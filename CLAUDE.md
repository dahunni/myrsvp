# myrsvp — RSVP e-reader for Android

An offline e-reader that shows a book **one word at a time** (RSVP, Rapid Serial Visual Presentation)
with a fixed focus letter (ORP) so the eyes never move. Built first for the **Qin F22 Pro** keypad
phone, released publicly on Google Play (publisher: TH-Operations GmbH), so it must also work on
normal touch phones.

## Read these first
- `docs/SPEC.md` — features, RSVP engine rules (timing, ORP), book import, controls, storage
- `docs/DESIGN.md` — colors, type, sizes and layout of every screen (source of truth for UI)
- `design/mockups/*.dc.html` — the approved mockups as HTML (read the markup; sizes are px at 640×960)
- `docs/RELEASE.md` — Play Store requirements; keep the app compliant from day one

## Target device
- Qin F22 Pro: 3.54" IPS, **640 × 960 px**, Android 11/12, D-pad + numeric keypad + touchscreen.
- Assume xhdpi (2.0) → **320 × 480 dp**. Verify on the device with `adb shell wm density`.
- Create an emulator profile 640×960 / xhdpi to test layouts.

## Tech stack (decided)
- Kotlin, Jetpack Compose, single Activity. Material3 only as a base; use our own theme (`ui/theme`).
- minSdk 26, targetSdk as configured. No dynamic color.
- **No INTERNET permission, no analytics, no ads, no accounts.** Everything stays on the device.
- Book import via Storage Access Framework (`ACTION_OPEN_DOCUMENT`), no storage permissions.
- Settings + reading positions: Jetpack DataStore. Books: app-internal files (format in SPEC).
- Allowed extra dependencies: `org.jsoup:jsoup` (EPUB HTML → text), `androidx.datastore`,
  `kotlinx.serialization`, `androidx.navigation:navigation-compose`. Ask before adding others.

## Code layout
```
de.dahunni.myrsvp
  engine/   pure Kotlin, no Android imports: tokenizer, ORP index, word timing, sentence index
  book/     import (EPUB, TXT), conversion to the internal format, library storage
  data/     DataStore settings + per-book progress
  ui/       theme/, reader/, library/, menu/, settings/, info/  (one package per screen)
  input/    key + gesture mapping → app actions (one place, so Qin keycodes are easy to adjust)
```

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Install on connected phone: `./gradlew installDebug`

## Rules
- `engine/` must be fully unit-tested (ORP index, timing, abbreviations, sentence jumps).
- The whole app must be 100 % button-navigable: every screen fully usable with the D-pad alone, every
  action reachable by a key (holding OK where a ☰ key may be missing). Touch only mirrors keys, never
  replaces them; reader gestures go through the same direction mapping as the D-pad (`input/`).
- Every focusable item shows a visible focus state (see DESIGN.md).
- Keep the screen on while playing; let it sleep when paused.
- Match DESIGN.md exactly; if something is unclear, follow the mockup markup.

## Milestones
1. Engine + reader screen with a hard-coded sample text (dark theme), keypad controls.
2. TXT + EPUB import, library screen, saved position per book.
3. Menu, chapters, book info, settings (incl. light theme + focus color), persistence.
4. Touch gestures, empty states, app icon, release build (see RELEASE.md).
