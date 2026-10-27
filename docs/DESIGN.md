# Design — myrsvp

Mockups: `design/mockups/` (HTML, px at 640 × 960) and the live canvas
https://claude.ai/artifact/X9DpZKhcrxuJS6t6MEmAVz (private to Tim).
All values below are in **dp/sp** = mockup px ÷ 2 (Qin F22 Pro, xhdpi, 320 × 480 dp).
Layouts must still scale to bigger phones: keep paddings, center the word area, cap content width at 480 dp.

## Look
Classic RSVP style (Spritz / RSVP Nano): a single word between two thin guide lines, small tick
marks above and below the focus letter, the focus letter in an accent color. Calm, no decoration,
no gradients, no cards with shadows.

## Colors
| Token          | Dark      | Light     | Use |
|----------------|-----------|-----------|-----|
| background     | #0E0E0E   | #FFFFFF   | screen |
| text           | #EDEDED   | #111111   | word, titles |
| textMuted      | #8A8A8A   | #6B6B6B   | labels, chapter, hints, % |
| textFaint      | #5C5C5C   | #9A9A9A   | following text in Paused, finished bar |
| guideLine      | #2A2A2A   | #D9D9D9   | lines above/below word, progress track |
| tick           | #4A4A4A   | #B5B5B5   | ORP tick marks |
| divider        | #222222   | #E6E6E6   | line above key-hint bar |
| rowFocused     | #262626   | #EFEFEF   | focused list row background |
| dashedBorder   | #3A3A3A   | #C9C9C9   | "Add books" row |

Focus-letter accent (setting "Focus letter"), also used for the focus outline in lists:
| Option | Dark    | Light   |
|--------|---------|---------|
| Red (default) | #FF4B3E | #D21F1F |
| Gold   | #E8B04A | #B07A12 |
| Blue   | #4FA3FF | #1F5FD2 |

## Type
- Reading word + Paused sentence: **Literata** (Google Fonts, OFL) Regular. Bundle in `res/font/`.
- UI: **Alte DIN 1451 Mittelschrift** (Peter Wiegel, OFL; `res/font/alte_din_1451.ttf`). It has a single weight:
  the weights below are mapped to it (no synthetic bold), hierarchy comes from size and color.
- Until the fonts are added, `ui/theme/Type.kt` falls back to system Serif / SansSerif.

| Style            | Font     | Size  | Weight | Notes |
|------------------|----------|-------|--------|-------|
| word             | Literata | 42 sp (Word size L); M = 36, S = 30 | 400 | line height 1.3 |
| pausedSentence   | Literata | 18 sp | 400 | line height 1.5 |
| pausedNext       | Literata | 14 sp | 400 | textFaint |
| screenTitle      | DIN 1451 | 17 sp | 600 | "Library", "Menu", "Settings" |
| bookTitle        | DIN 1451 | 12 sp | 600 | reader header |
| rowTitle         | DIN 1451 | 13 sp | 500 (600 when focused) | list rows |
| meta             | DIN 1451 | 10 sp | 400 | chapter, author, %, time left |
| wpm              | DIN 1451 | 11 sp | 500 | reader header, right |
| hint             | DIN 1451 | 9.5 sp| 400 | key-hint bar |

## Reader (Main / ReaderLight mockups)
- Screen padding: top 18, sides 16, bottom 14 dp.
- Header row: book title (bookTitle) + chapter below it (meta, gap 3 dp); wpm right-aligned.
- Word area fills the remaining height, word band vertically centered:
  - Guide lines 1 dp (guideLine) above and below the band, full content width.
  - The **center** of the focus letter sits at a fixed x: 128 dp into the 288 dp content width (128/288 of the
    width on bigger phones; ≈ 144 dp from the screen edge on the Qin). The part before it ends at the letter, the
    rest starts after it. Centering the letter (not its left edge) keeps it and the ticks still for narrow and wide letters.
  - Ticks: 1.5 × 13 dp (tick color), centered on the focus letter, inside the lines above and below the word.
  - Guide lines + ticks can be turned off in Settings.
- Footer: progress bar 3 dp (track guideLine, fill textMuted, radius 1.5 dp) → row "12 % · Chapter 1 of 61" left,
  "5 h 6 min left" right (meta) → key-hint bar: 0.5 dp divider, 8 dp top padding, three hints spread
  evenly, e.g. "▲▼ Speed · OK Pause · ◀▶ Sentence" (hint). Hide the key-hint bar on devices without a D-pad
  (no `Configuration.NAVIGATION_DPAD`), showing touch hints once on first launch instead.

## Paused
- Header right shows a pill "Paused": bg = text color, label = background color, 10 sp SemiBold, padding 3×7 dp, fully rounded.
- Center: current sentence (pausedSentence, textMuted) with the current word in text color and a 1.5 dp
  underline in the accent; below it (gap 14 dp) the following text (pausedNext, textFaint), paragraphs on new
  lines, as many complete lines as fit — one **page**. 12 dp gap to header and footer.
- ▲▼ page: ▼ starts the next page with the first sentence that wasn't fully visible (nothing is skipped),
  ▲ goes back to the page that ends right before the current sentence.
- Footer: progress + "12 % · Chapter 1 of 61" left, wpm right; hints "OK Resume · ▲▼ Page · ◀▶ Sentence".

## Lists (Library, Menu, Chapters, Settings)
- Padding: top 18, sides 12, bottom 14 dp. Screen title with 10 dp bottom spacing (sides 4 dp extra inset).
- Rows: full width, padding 8 × 9 dp (Library) / 10 × 9 dp (Menu, Settings), radius 7 dp, 3 dp gap.
- **Focused row:** background rowFocused + 1.5 dp outline in the accent color, title weight 600.
  This is the only focus indicator and must be clearly visible on the D-pad.
- Library row: title (rowTitle 13 sp) → author (meta) → progress bar 2.5 dp + % right (min width 28 dp).
  Finished books: 50 % opacity, "Done" instead of %, at the bottom of the list.
- "Add books" row: transparent, 1 dp dashed border (dashedBorder), radius 7 dp, plus icon + label (11 sp, #BDBDBD dark).
- Menu row: label left, optional value right (meta), e.g. "Chapters — 1 of 61".
- Settings row: label left, value right; the focused row shows "◀  350 wpm  ▶" in text color, others the
  plain value in textMuted.
- Key-hint bar at the bottom like the reader: Library "▲▼ Move · OK Open · ☰ Menu", Menu "▲▼ Move · OK Select · ↩ Back",
  Settings "▲▼ Move · ◀▶ Change · ↩ Back".

## Motion
None for words (instant swap, no fade). Lists: no animations except a 150 ms focus-background change.
Speed change: no overlay on the word. The header's wpm label pops into a pill (rowFocused, text color, scale 1.15,
150 ms in), holds 800 ms, then settles back in 300 ms. The pill is drawn around the label, so the layout never shifts.
