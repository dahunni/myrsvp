# Release checklist — Google Play

Publisher: TH-Operations GmbH (organization account → no 12-tester / 14-day closed test required).

## Account (do early)
- [ ] D-U-N-S number for TH-Operations GmbH (free, can take 1–2 weeks)
- [ ] Google Play Console organization account ($25 one-time) + identity verification
- [ ] Payments profile for the GmbH (only if the app is paid or has a pro unlock)

## App
- [ ] Final app name checked (Play search + trademark search); applicationId stays `de.dahunni.myrsvp`
      unless changed BEFORE the first upload (it can never change afterwards)
- [ ] targetSdk meets Play's current requirement (Android 16 / API 36 or higher since 31 Aug 2026)
- [ ] No INTERNET permission, no analytics, no ads → Data safety form: "no data collected or shared"
- [ ] Works with D-pad only AND touch only; tested on Qin F22 Pro + a normal phone + 640×960 emulator
- [ ] Release build signed via Play App Signing, uploaded as Android App Bundle (.aab)
- [ ] Open-source licenses screen (jsoup, fonts OFL, credits to RSVP Nano + pocket-reader, MIT)
- [ ] Pre-launch report free of crashes / ANRs

## Store listing
- [ ] App icon 512 × 512, feature graphic 1024 × 500
- [ ] 2–8 phone screenshots (reader dark, reader light, paused, library, settings)
- [ ] Short description (80 chars) + full description, English + German
- [ ] Privacy policy URL (e.g. on th-operations.de): app works offline, collects nothing
- [ ] Content rating questionnaire, target audience, app category (Books & Reference)

## Review
- [ ] Optional open beta first, then production (review can take up to ~7 days)
