# FlowRep – Design & Rebrand: Umsetzungsplan & Status

Umsetzung des Style-Guides `Design.txt` als vollständiges Designsystem plus Rebrand von "DropSync" auf **FlowRep**. Markenpalette (Schwarz `#0D0D0D` / Weiß `#FFFFFF` / Lime `#DFFF2F` / Grau) und Radien sind bereits in [Theme.kt](core/designsystem/src/main/kotlin/com/dropsync/core/designsystem/theme/Theme.kt) umgesetzt; ergänzt werden Raleway-Typografie, Spacing-Tokens, Marken-Komponenten und ein Screen-Redesign.

Grundsätze: offline (Raleway als gebündelte OFL-TTF, kein Netz), keine neuen Fitness-Features, `applicationId`/`namespace` bleiben `com.dropsync.*`, 3-Tab-IA (MUSIC/TRAINING/SETTINGS) bleibt. User-facing Strings mit echten Umlauten; Code/Kommentare ASCII.

## Statustabelle

| Phase | Inhalt | Status |
| --- | --- | --- |
| 1 | FlowRep Design-Mockups (imagegen-frontend-mobile) | Zurückgestellt (Bilddienst 40500, wird nachgeholt) |
| 2 | Design-Tokens + Raleway-Typografie + Spacing (`:core:designsystem`) | Abgeschlossen |
| 3 | Marken-Komponenten (Buttons, BrandCard, ProgressRing) | Abgeschlossen |
| 4 | Rebrand FlowRep (app_name, Launcher-Icon) | Abgeschlossen |
| 5 | Screen-Redesign: Bibliothek (`:feature:library`) | Offen |
| 6 | Screen-Redesign: Now-Playing + Mini-Player (`:feature:player`) | Offen |
| 7 | Timer + Bottom-Nav + globaler Feinschliff | Offen |

## Phase 2 – Design-Tokens + Typografie (Abgeschlossen)

- Neu [Type.kt](core/designsystem/src/main/kotlin/com/dropsync/core/designsystem/theme/Type.kt): `BrandFontFamily` (gekapselter Raleway-Swap-Punkt) + `DropSyncTypography` mit der Skala aus `Design.txt` (Hero/Display eng mit negativem Tracking, Body luftig, Caps-Labels), tabellarische Ziffern für große Zahlen.
- Neu [Spacing.kt](core/designsystem/src/main/kotlin/com/dropsync/core/designsystem/theme/Spacing.kt): `object Spacing` mit 4/8/12/16/24/32/48/64/80/96/120 dp.
- [Theme.kt](core/designsystem/src/main/kotlin/com/dropsync/core/designsystem/theme/Theme.kt): `MaterialTheme(typography = DropSyncTypography, ...)`.
- Raleway als statische OFL-TTF (400/500/600/700/800) in `core/designsystem/src/main/res/font/` gebündelt und in `BrandFontFamily` verdrahtet (offline, kein Provider); OFL-Notice in `THIRD_PARTY_NOTICES.md`.

## Phase 3 – Marken-Komponenten (Abgeschlossen)

- Neu `component/Buttons.kt` (`BrandButtonPrimary/Secondary/Ghost`, Pill 56dp, Bold-Label, Press-Scale), `component/BrandCard.kt` (Radius 24, Hairline-`outline`, weiche Elevation), `component/ProgressRing.kt` (feder-animierter Lime-Ring mit zentralem Slot), `component/CountUpText.kt` (hochzählende Zahl für Peak-End).

## Phase 4 – Rebrand FlowRep (Abgeschlossen)

- `app_name` → „FlowRep"; Launcher-Hintergrund `#0D0D0D`; [ic_launcher_foreground.xml](app/src/main/res/drawable/ic_launcher_foreground.xml) als geometrisches Lime-„F"-Monogramm (dient auch als monochrome Variante). Projekt-/Paketname bleiben `com.dropsync`; README mit Marken-Hinweis ergänzt.

## Verifikation je Phase

- `./gradlew spotlessApply` (separat) → betroffene Modul-Tests + `:app:assembleDebug` → `:core:testing:test` (Architektur-Wächter) + `spotlessCheck`.
