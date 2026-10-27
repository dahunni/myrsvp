# Product spec — myrsvp

Concept and timing rules adapted from the MIT-licensed projects RSVP Nano (ionutdecebal/rsvpnano)
and pocket-reader (laurensvanhelvoort/pocket-reader). Credit both in the app's About/licenses screen.

## 1. Screens
| Screen   | Purpose |
|----------|---------|
| Library  | List of books, sorted by last read; finished books greyed out at the bottom; "Add books" row at the end |
| Reader   | One word at a time with fixed focus letter, header (title, chapter, wpm), progress + time left, key hints |
| Paused   | Same screen, word display replaced by a page: the current sentence (current word underlined) + the following text dimmed; ▲▼ page |
| Menu     | Continue reading · Chapters · Book info · Settings · Start from beginning · Library |
| Chapters | List of chapters, current one focused; OK jumps to chapter start |
| Book info| Title, author, year (if known), word count, reading time at current speed, progress |
| Settings | Speed · Theme (Dark/Light) · Focus letter (Red/Gold/Blue) · Word size (S/M/L) · Pause at sentences · Ramp-up · Guide lines |

App starts in the Library; the book read last is the first row and has the focus, so OK continues it.
☰ in the Library (or a long press on a book) opens a small menu: Add books · Settings · Remove book.

## 2. RSVP engine (`engine/`, pure Kotlin)
**Focus letter (ORP) index** by word length (letters only, ignore surrounding punctuation):
| length | 1 | 2–5 | 6–9 | 10–13 | 14+ |
|--------|---|-----|-----|-------|-----|
| index  | 0 | 1   | 2   | 3     | 4   |

**Timing** (base = 60 000 / wpm ms per word; 300 wpm → 200 ms):
- Words longer than 6 letters: +8 % per extra letter.
- Ends with `,` `;` `:` `–` `—`: +0.5 × base.
- Sentence end (`.` `!` `?` `…`, also with closing quote/bracket): +1.0 × base.
- Paragraph end: +1.5 × base (instead of the sentence bonus).
- Abbreviations and initials are NOT sentence ends: Mr. Mrs. Ms. Dr. St. Prof. Jr. Sr. vs. e.g. i.e. etc. no.
  and single capital letter + dot (e.g. "H. G. Wells").
- Ramp-up (setting, default on): first 8 words after start/resume go linearly from 1.6× to 1.0× duration.
- "Pause at sentences" (default on): when the user pauses during playback, stop at the end of the current sentence.

**Speed:** 100–900 wpm in steps of 25, default 300, remembered globally.

**Long words:** if a word doesn't fit the available width at the chosen size, split it at an existing
hyphen, else into roughly equal chunks (ideally between syllables) and show them in sequence.

**Navigation:** keep a sentence index. "Back" once → start of current sentence; pressed again within
1.5 s → start of previous sentence. "Forward" → start of next sentence.

## 3. Controls
Map everything in `input/` so Qin keycodes can be adjusted in one place. Add a hidden debug screen
(Settings → tap version 5×) that shows the last received keycode, to verify the Qin's real codes.

| Key (KeyEvent)                | Reader (playing/paused)        | Lists            |
|-------------------------------|--------------------------------|------------------|
| DPAD_UP / DPAD_DOWN           | playing: speed +25 / −25 wpm; paused: previous / next page | move focus |
| DPAD_CENTER / ENTER           | play / pause                   | select           |
| DPAD_LEFT                     | back a sentence (see above)    | — (Settings: change value) |
| DPAD_RIGHT                    | next sentence                  | — (Settings: change value) |
| MENU, or long-press OK        | open Menu                      | Library: book menu (☰ / hold OK) |
| BACK                          | pause; if paused → Library     | back             |
| Number keys 1–9 (paused only) | jump to 10 %…90 % of the book  | —                |

Touch (for normal phones) mirrors the D-pad exactly, through the same mapping (`input/`): tap = OK,
a swipe = the arrow in the finger's direction (up = ▲: faster while playing, previous page while paused;
down = ▼; left = ◀ sentence back; right = ▶ sentence forward), long-press = Menu. Lists: normal tap/scroll,
long-press = what holding OK does.

**Button rule:** the whole app is 100 % usable with keys alone. Touch only ever adds a second way to do
something a key already does; nothing is touch-only. Holding OK replaces ☰ on devices without a menu key.

Show a speed change by briefly highlighting the wpm in the header (never over the word). Keep the screen on only while playing.

## 4. Books
**Import:** "Add books" opens the system file picker (EPUB, TXT; PDF later, optional). Multiple files allowed.
Convert on import (background coroutine + progress), then store only the converted file.
Project Gutenberg files: their license header/footer is skipped (TXT markers, EPUB `pg-header`/`pg-footer`),
TXT title/author come from the `Title:`/`Author:` header lines, and their EPUB `dc:date` (ebook date) is not a year.
Images are dropped, except single-letter drop caps (`<img alt="N">`), whose letter is kept.
A run of chapter lines directly after each other is a table of contents, not chapters.

Nothing goes to cloud backup (`data_extraction_rules.xml`); a direct device-to-device transfer may copy it.

**EPUB:** zip → `META-INF/container.xml` → OPF → spine order → each XHTML to text with jsoup
(keep paragraph breaks, `<h1–h3>` become chapter markers, drop images/tables/footnote markers).
Chapters from the EPUB3 nav document or the EPUB2 NCX; fall back to headings.
Metadata: dc:title, dc:creator, dc:language, dc:date (year only if it is a real publication date).
DRM-protected EPUBs (`META-INF/encryption.xml` with content encryption): show a clear error.

**TXT:** UTF-8 (fall back to Windows-1252), blank line = paragraph, lines like "Chapter 3" /
"CHAPTER III" / "Kapitel 3" = chapter markers. Title from filename.

**Internal format** (`files/books/<uuid>.prb`, plain UTF-8):
```
PRB1
title: Pride and Prejudice
author: Jane Austen
language: en
year: 1813
chapter: 0 Chapter 1
chapter: 2348 Chapter 2
---
<one paragraph per line>
```
Chapter numbers are byte offsets from the first byte after the `---` line.
Re-importing a book with the same title + author replaces it and keeps the reading position.

**Progress per book** (DataStore): byte offset of the current sentence start, last-opened time,
finished flag (set when the end is reached). Library shows % and "Done".

## 5. Non-goals (v1)
Images, tables, footnotes, DRM, cloud sync, accounts, text-to-speech, highlights/notes.
