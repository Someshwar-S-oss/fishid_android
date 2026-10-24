# FishID — App Plan

Source requirements: `docs/fish software template.pdf` (professor's deck). Decisions below were confirmed in planning.

| Decision | Choice |
|---|---|
| Model | TFLite (LiteRT) **image classifier**, on-device |
| Model delivery | Bundled in APK **+ OTA download** (manifest, checksum, rollback) |
| Species data / prices | Bundled JSON, **refreshed from a static remote JSON** (no server) |
| Price | One average ₹/kg per species + "last updated" date |
| Catalogue size | ~10–30 species at launch |
| Data authoring | Professor's team fills an **Excel / Google Sheet** → script → JSON |
| Scan mode | **Hybrid**: live auto-lock, auto-falls back to tap-to-capture on slow phones |
| Low confidence | Show **top 3 candidates** to compare by eye |
| Audience | General public: big text, high contrast, Tamil-first, budget phones, sunlight |
| v1 extras | Species catalogue, scan history, pick from gallery, report-wrong-ID (form + local queue, backend later) |
| Name | **FishID** |
| Languages | English + Tamil. All text in JSON for human/Google translation; nothing hand-translated by the dev |

---

## 1. Screens

Navigation: bottom bar with 3 tabs — **Scan** (home) · **Fish guide** · **History**. Settings from a gear icon in the top bar. Every icon has a text label (public users).

### S1 · First launch (once)
1. **Language** — two large buttons showing each language in its own script (`English`, `தமிழ்`). Default highlighted = phone language.
2. **How it works** — 3 swipeable cards: Scan → Identify → Learn (straight from deck slide 2). "Skip" always visible.
3. **Camera permission** — explained *before* the system dialog ("only used to identify fish, photos stay on your phone"). Denied → "Choose a photo instead" (gallery still works) + "Open settings".

### S2 · Scan (home)
```
┌─────────────────────────────┐
│ FishID            A/அ   ⚙   │  top bar over camera (translucent)
│                             │
│   ┌──               ──┐     │  framing brackets (marigold)
│   │                   │     │  → turn green + haptic on lock
│   │      [ fish ]     │     │
│   └──               ──┘     │
│ Place the whole fish        │  hint chip, changes by state
│ sideways inside the frame   │
│  [🖼]      ( ◉ )      [⚡]   │  gallery · shutter · flash
├─────────────────────────────┤
│ Scan    Fish guide  History │
└─────────────────────────────┘
```
**Result card** (slides up over the camera when locked — as in deck slide 3):
- Fish photo thumb · **primary name** (Tamil vernacular in Tamil mode, common name in English mode) · secondary name in the other language · *scientific name* (italic)
- Confidence pill: `High match · 97%` (≥ threshold+10) / `Likely match · 82%`
- Price line: `₹400 per kg`
- Buttons: **View full details** (primary) · Scan again
- Swipe down = dismiss and keep scanning.

States: searching (brackets breathe softly) · hold still · locked · identifying (tap mode spinner ≤1 s) · low confidence (S3) · no match (tips list) · camera error.

### S3 · Not sure — compare (low confidence)
- User's photo on top (small).
- 3 candidate cards stacked: reference photo, names, %, **2–3 key field marks** (from morphology data — deck: "used to visually confirm a match").
- Each card: **This one** → profile (and logs the user's choice in history).
- Bottom: **None of these** → Report (S7).
- If top-1 is below a floor (e.g. 30%) or "background" class wins → "Couldn't identify" with retake tips instead.

### S4 · Species profile
Order is tuned for shoppers: price and food value first, science one scroll deeper. (Deck order: names → picture → morphology → ecology → nutrition → price.)

```
[ reference photo carousel / "Your photo" toggle ]
குண்டல் மீன்                    ← primary name (display size)
Thumbprint Monocle Bream        ← secondary
Scolopsis bimaculata            ← italic serif
[ Least Concern ]  [ ₹400/kg ]  ← badges
─ Overview | Nutrition | How to identify | Habitat ─   sticky tabs, scroll-spy
```
- **Price card** — big `₹400` + `per kg`, "Updated 12 Oct 2026", note "Prices change with market and season."
- **Nutrition** — headline `21 g of protein in every 100 g`, then a single 100% stacked bar (moisture dominates at 76 g, so separate bars make protein look tiny), then the 5-row table from the deck.
- **How to identify** — key features as a bulleted list; fin counts as 4 stat tiles (dorsal spines/rays, anal spines/rays).
- **Habitat** — 6 icon tiles: distribution, habitat, feeding, vertical guild, depth, IUCN (IUCN badge uses official IUCN colours).
- Names block: common, scientific, scientific (Tamil), local name.
- Footer: Source · data version · **Wrong fish? Tell us** (→ S7) · Share.

### S5 · Fish guide (catalogue)
- Search field: matches English, Tamil, local name, scientific name **and romanised local names** (people type "kundal", not Tamil script — needs a `local_name_roman` column).
- 2-column grid of cards: photo, primary name, secondary name, ₹/kg.
- Sort: Name · Price low→high · Price high→low. No filters at 10–30 species; add when the list passes ~50.

### S6 · History
- On-device only. Grouped Today / Yesterday / date. Row: user's photo, name (or "Not identified"), %, time.
- Tap → profile with "Your photo" selected. Swipe to delete; Clear all in overflow (confirm).
- Capped at 200 entries, oldest dropped.

### S7 · Report wrong identification
- The photo · "Our guess: X (82%)" · **Which fish is it?** (searchable species picker / "I don't know") · market or place (optional text — no GPS permission) · note (optional) · privacy line.
- **Send report** → saved to `filesDir/reports/<uuid>/{photo.jpg, report.json}`. That folder *is* the upload queue; the uploader is added when the backend exists. Copy says "saved", not "sent".

### S8 · Settings
Language · Scan mode (Automatic / Live / Tap to scan) · Fish data version + Model version + **Check for updates** · Privacy · About (institution, credits, data sources — content needed from professor).

---

## 2. User flows

```
A. First launch   Install → Language → How it works (skip ok) → Camera permission → Scan
B. Happy path     Scan → point at fish → brackets lock (~0.5 s stable) → result card → View details → Profile
C. Unsure         Scan → lock below threshold → Compare 3 → "This one" → Profile
                                                        └→ "None of these" → Report → saved → back to Scan
D. Gallery        Scan → gallery icon → system photo picker → identify → (B) or (C)
E. Browse         Fish guide → search "kundal" → card → Profile
F. History        History → past scan → Profile (with user's photo)
G. Offline        Everything works except updates. No error banners for being offline.
H. Update         App start (≤ once/24 h, Wi-Fi preferred for model) → fetch manifest → download →
                  verify sha256 → test-load → swap on next scan session. Failure → keep current, silently.
```

---

## 3. Hybrid scan mode (live ↔ tap)

- **Live**: CameraX `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, RGBA output) → crop to the on-screen frame → model.
  Smooth with a moving average over the last ~5 results. **Lock** when the same species is top-1 and ≥ its threshold for 500 ms → haptic + result card. Analysis pauses while the card is open.
- **Auto fallback** (setting = Automatic): after the first 15 frames, if **median inference > 150 ms** (< ~6 fps, feels laggy), or `ActivityManager.isLowRamDevice()`, or thermal status ≥ `THERMAL_STATUS_SEVERE` → switch to **Tap** and show one-time hint "Switched to tap mode for smoother scanning on this phone". Re-measured each app launch (phone may be cool / less busy next time).
- **Tap**: freeze frame on shutter → run once → result. Same result UI.
- Shutter button is always visible, so tap works in live mode too.
- The 150 ms threshold is a **tuning knob in one constant** — calibrate on 2–3 real budget phones at the market.

---

## 4. ML model contract (drop-in replacement)

The app never hard-codes the model. A model package is two files:

```
model/
  model.tflite
  model.json
```
```json
{
  "model_version": 3,
  "min_app_version": 1,
  "min_data_version": 5,
  "input": { "mean": [0.485, 0.456, 0.406], "std": [0.229, 0.224, 0.225], "range": "0-1" },
  "output": "softmax",
  "labels": ["scolopsis_bimaculata", "lutjanus_argentimaculatus", "__background__"],
  "default_threshold": 0.70
}
```
- Input size/dtype (float32 or uint8 quantised) is **read from the tensor**, not the JSON. Only normalisation lives in the JSON.
- `output`: `softmax` or `logits` (app applies softmax).
- `labels` are **species ids** (snake_case scientific name), matching ids in `species.json` — never display names.
- `__background__` = "not a fish / not a known species" class. **Ask the ML team to train one**; it is the cheapest way to avoid confidently naming a shoe.
- Per-species thresholds live in species data (`confidence_threshold`, from the deck's metadata), falling back to `default_threshold`.

**Load-time checks** (any failure → reject the package, keep the previous one): output size == labels count · every non-background label exists in species data · `min_app_version` / `min_data_version` satisfied · a dummy inference runs.

**What the ML team must hand over**: `.tflite` (INT8 or FP16 quantised, target < 15 MB, 224×224 or similar), `model.json`, and a **parity set** — ~20 test images + their expected Python outputs. The app's debug build runs the same images and asserts top-1 matches and probabilities differ by < 0.02. This catches preprocessing mismatches (RGB/BGR, normalisation, crop), the #1 cause of "works in Python, wrong on phone".

---

## 5. Data

### 5.1 Files (bundled in `assets/`, same layout for OTA)
```
assets/
  model/model.tflite, model.json
  data/species.json            language-neutral facts
  data/text.en.json            species text, English (from spreadsheet)
  data/text.ta.json            species text, Tamil (from translator)
  data/images/<id>_1.webp ...
```
`species.json` (no translatable prose in here — only ids, numbers, codes, and names that are *data*, not translations):
```json
{
  "data_version": 5,
  "updated": "2026-10-12",
  "species": [{
    "id": "scolopsis_bimaculata",
    "scientific_name": "Scolopsis bimaculata",
    "scientific_name_ta": "<from professor>",
    "local_name_ta": "<from professor>",
    "local_name_roman": "<from professor, e.g. how people type the local name in English letters>",
    "images": ["scolopsis_bimaculata_1.webp"],
    "meristics": { "dorsal_spines": 10, "dorsal_rays": 9, "anal_spines": 3, "anal_rays": 7 },
    "habitat": ["marine", "reef_associated"],
    "feeding": "carnivore",
    "vertical": "demersal",
    "depth_m": [0, 60],
    "iucn": "lc",
    "nutrition_g": { "protein": 20.91, "fat": 1.10, "moisture": 76.30, "ash": 1.20, "carbohydrate": 0.49 },
    "price_inr_per_kg": 400,
    "price_updated": "2026-10-12",
    "confidence_threshold": 0.75,
    "source": "<citation>"
  }]
}
```
`text.en.json` (keyed by species id → what a translator gets):
```json
{
  "scolopsis_bimaculata": {
    "common_name": "Thumbprint Monocle Bream",
    "distribution": "East and West coasts of India",
    "field_marks": ["Blue stripe joining the eyes", "1–2 brownish blotches on the upper body", "..."]
  }
}
```
- Coded values (habitat, feeding, vertical guild, IUCN) are **codes**, translated once in UI strings (`habitat_marine`, `iucn_lc` …). The spreadsheet uses dropdowns for these, so there are no typos and no per-species translation.
- Tamil scientific name and Tamil local name are **expert data from the professor**, not machine-translatable — they stay as spreadsheet columns.
- Missing Tamil text → English shown for that field.

### 5.2 Spreadsheet → JSON
`tools/species_template.xlsx` (one row per species, dropdowns for codes, `field_marks` one per line in a cell) → `tools/sheet_to_json.py` validates (required fields, numeric ranges, codes, image files exist, nutrition sums ≈ 100 g) and emits `species.json` + `text.en.json`. Photo guidance for the team: side view, whole fish, plain background (like their sample), ≥ 1200 px, converted to WebP by the script.

### 5.3 Remote updates (static hosting — GitHub Pages / Firebase Hosting)
```json
{ "data":  { "version": 6, "url": ".../data-6.zip",  "sha256": "..." },
  "model": { "version": 4, "url": ".../model-4.zip", "sha256": "...", "wifi_only": true } }
```
WorkManager job, ≤ once per 24 h. Download → verify sha256 → unzip to `filesDir/{data,model}/v<N>/` → validate (section 4) → store active version in prefs. Bundled assets are the permanent fallback. Data and model update together when a model adds species (`min_data_version`).

---

## 6. Localisation (English + Tamil)

**Rule: no user-facing text in Kotlin.** No Tamil is written by the developer.

```
i18n/ui.en.json       English UI strings — SOURCE OF TRUTH, send this to translator / Google Translate
i18n/ui.ta.json       Tamil, returned by translator (same keys). Currently {}
i18n/ui.fixed.json    never translated (language names)
tools/i18n.py         → generates res/values/strings.xml and res/values-ta/strings.xml
```
- Run `python tools/i18n.py` after any JSON change. Generated XML is never edited by hand.
- Placeholders are `{name}` in JSON (translators and Google Translate leave these alone more reliably than `%1$s`). The script turns them into positional args, so Tamil can reorder them freely, and **fails if a translation lost or renamed a placeholder** — re-check Google Translate output for this.
- Prints how many strings are translated; untranslated keys fall back to English.
- Species content follows the same pattern: `data/text.en.json` → translator → `data/text.ta.json`.
- **Translator checklist:** keep keys unchanged · keep `{…}` untouched · keep "FishID" untranslated · native Tamil speaker reviews machine output (fish terminology especially).
- Language switch: `AppCompatDelegate.setApplicationLocales()` with `autoStoreLocales` (persists, backported to API 24) + `res/xml/locales_config.xml` so Android 13+ shows FishID in system per-app language settings. Needs `appcompat` and `MainActivity : AppCompatActivity`.
- Digits: Western digits in both languages; ₹ formatted with Indian grouping (`₹1,200`).
- Layout rules for Tamil: Tamil strings run ~30–50% longer → no fixed-width text containers, buttons wrap to 2 lines, no letter-spacing or ALL-CAPS on Tamil (caps-style labels in the deck are English-only treatment).

---

## 7. Visual design

### Colour (light theme primary; camera screen is always dark)
| Token | Hex | Use |
|---|---|---|
| Primary — Deep Teal | `#0E6E7E` | buttons, active tab, links |
| Ink | `#0F1B2D` | all body text (no light-grey text — sunlight) |
| Ink-muted | `#4A5565` | secondary text (≥ 7:1 on white) |
| Background | `#F5F7F8` | screen bg |
| Surface | `#FFFFFF` | cards |
| Accent — Marigold | `#F2A541` | scan brackets, highlights (deck's orange brackets) |
| Success | `#2E9E5B` | lock state |
| IUCN | official palette | LC green → CR red badges |

Dark theme supported via M3 tokens. **Dynamic colour (Material You) off** — the brand must look the same on every phone (`Theme.kt` currently has `dynamicColor = true`).

### Typography
- **UI: Hind Madurai** (Latin + Tamil drawn as one family, so mixed-script lines match in weight and height). Bundle Regular / Medium / SemiBold / Bold in `res/font` — no downloadable fonts (offline).
- **Scientific names: Noto Serif Italic** — binomial names are conventionally italic, Hind has no italic, and a serif italic nods to the deck's style.
- Fallback if Hind renders poorly on test phones: Mukta Malar.

| Role | Size / line height (sp) | Weight |
|---|---|---|
| Price figure | 40 / 44 | Bold, tabular digits |
| Display (fish name on result/profile) | 28 / 38 | Bold |
| Headline (section) | 22 / 32 | SemiBold |
| Title (card) | 18 / 28 | SemiBold |
| Body | 16 / 26 | Regular — never smaller for prose |
| Label | 14 / 20 | Medium |
| Caption | 13 / 20 | Regular |

Line heights are ~1.5× (looser than Material default) because Tamil vowel signs need vertical room. Respect system font scale up to 200%; test at 130% and 200%.

### Layout & touch
4 dp grid · 16 dp screen gutter · card radius 16 dp · buttons 56 dp tall (pill) · min touch target 48 dp · shutter 76 dp · icons: Material Symbols Rounded, always with labels.

### Motion
Result card spring-up 250 ms · bracket lock: colour + slight scale 200 ms + haptic · respect "remove animations" setting. Nothing decorative.

### Bilingual name rule
Primary name follows the UI language; the other language always appears as secondary text. A Tamil-mode shopper still sees the English name; an English-mode user still sees the local market name — useful in both directions.

---

## 8. Architecture

Single module, Jetpack Compose, plain ViewModels. No DI framework, no Room (history and reports are small JSON files) — add either when it hurts.

```
app/src/main/java/com/example/fishid/
  MainActivity.kt              AppCompatActivity, NavHost, bottom bar
  ml/Classifier.kt             loads package, preprocess (crop/resize/normalise), run, softmax, top-3
  ml/ScanController.kt         live smoothing + lock, latency monitor, live↔tap decision
  data/Species.kt              data classes + repository (bundled vs downloaded, text fallback)
  data/Updater.kt              WorkManager: manifest → download → verify → validate → activate
  data/History.kt              history JSON in filesDir, 200 cap
  data/Reports.kt              report folders in filesDir (future upload queue)
  ui/onboarding/  ui/scan/  ui/species/  ui/catalogue/  ui/history/  ui/report/  ui/settings/
  ui/theme/                    colours, Hind Madurai + Noto Serif Italic, type scale
app/src/main/assets/{model,data}/
app/src/main/res/values/, values-ta/   (generated), font/, xml/locales_config.xml
i18n/   tools/i18n.py   tools/sheet_to_json.py   tools/species_template.xlsx
```

**Dependencies to add:** CameraX (core, camera2, lifecycle, view) · LiteRT (`com.google.ai.edge.litert`) · navigation-compose · kotlinx-serialization-json · Coil (image decoding/downsampling) · WorkManager · AppCompat · core-splashscreen · lifecycle-viewmodel-compose. Also bump the stale versions in `libs.versions.toml` (core-ktx 1.10, lifecycle 2.6, activity 1.8 are old for compileSdk 37).

GPU delegate skipped — XNNPACK on CPU handles a quantised 224 px classifier in ~20–60 ms on budget phones. Add it only if the latency monitor shows most devices falling back to tap mode.

**Before Play Store:** change `applicationId` from `com.example.fishid` (Play rejects `com.example`).

---

## 9. Quality targets

- Cold start → camera ready < 1.5 s on a mid-range phone; first live result < 1 s.
- APK < 30 MB (model ≤ 15 MB + ~30 species × 2 WebP photos).
- 100% functional offline.
- No account, no analytics in v1, camera is the only permission. Photos never leave the phone except an explicit report (once a backend exists).
- Accessibility: TalkBack labels on all controls, 200% font scale, contrast ≥ 4.5:1 (body text ≥ 7:1).

---

## 10. Build phases

1. **Foundation** ✅ theme, fonts, i18n pipeline, language switch, back stack, data loading with the deck's sample species.
2. **Profile + Fish guide** ✅ profile (photo carousel, sticky scroll-spy tabs), catalogue, search in every script.
3. **Scanning** ✅ CameraX + LiteRT classifier, hybrid live/tap with auto-fallback, lock, compare-3, no-match. Runs on a **placeholder model** (ImageNet MobileNet v1 with fish-like classes mapped to the sample species) until the ML team's model lands.
4. **Gallery, History, Report form** ✅ (reports queue in `filesDir/reports`, nothing is sent yet).
5. **OTA updates** — manifest, checksum, validation, rollback. *Not started.*
6. **Polish & field test** — native-speaker Tamil review, TalkBack, font scale, budget phones at a real market, tune `LIVE_MAX_MEDIAN_MS` + thresholds, Play Store (`applicationId`, pre-API-26 launcher webps). *Not started.*

Implementation notes (deviations from §8):
- **Real model bundled** (Repos/fishy `fish_fp16.tflite`, EfficientNet-B0 + LR head, 3 species). Contract field `input.resize`: `squash` (whole photo → 224, as trained) or `center_crop`. Camera frames are cropped to the 4:3 bracket box first, so fish keep training proportions; gallery photos go in whole. Downscaling halves repeatedly to match PIL's anti-aliased resize. `CatalogTest.bundledModelMatchesBundledData` fails the build if a dropped-in model's labels aren't in species.json.
- The model has **no background class**: any photo is forced into one of 3 species. The 0.7 threshold + "not sure" screen is the only guard; ask the ML team for a background class.
- Theme: Same as phone / Light / Dark in Settings (`AppCompatDelegate.setDefaultNightMode`, persisted). Phones are locked to portrait; tablets rotate and get a 640dp readable column.
- **LiteRT 1.4.2**, not 2.x: 2.x ships Kotlin 2.4 metadata that the project's Kotlin 2.2 can't read. Same `org.tensorflow.lite.Interpreter` API; bump when Kotlin is upgraded.
- No Coil, no navigation-compose: a 30-line downsampling image loader (`ui/Common.kt`) and a saveable route-string stack (`MainActivity.kt`) cover 10–30 species.
- Unit tests: `CatalogTest` (bundled data, Tamil fallback, search) and `ScanLogicTest` (aggregation, softmax, match/unsure/none, live lock, tap fallback, model validation).

## 11. Still needed from others

**ML team:** model package per section 4 · `__background__` class · per-species thresholds from validation data · parity image set.
**Professor's team:** filled spreadsheet (all species) · Tamil scientific + local names · romanised local names · 2+ reference photos per species · price source and how often it's updated · About/credits text + institution logo (optional).
**Translator:** `i18n/ui.en.json` now; `data/text.en.json` once the spreadsheet is filled.
**Later:** backend for reports (folder format is ready for it), hosting URL for the update manifest.

## 12. React Native UI (branch `react-native-ui`)

The Compose UI is replaced by React Native 0.88 (new architecture, Hermes). Kotlin keeps the model and data layer unchanged in behaviour: `ml/` (Classifier, ScanLogic), `data/` (Catalog, History, Reports), `scan/ScanEngine.kt` (Compose state swapped for listeners).
- `bridge/FishIdBridge.kt` is the only seam: module `FishID` (catalog/search, history, reports, prefs, language, gallery pick, shutter/torch) + view `FishCameraView` (CameraX preview running ScanEngine). Data crosses as the same snake_case JSON as the asset files.
- JS lives at the repo root: `index.js`, `src/` (App shell + stack nav, `ui.tsx` components, `screens/`). UI text still comes only from `i18n/*.json`; new keys were added in English only.
- Design: Cal AI (cream, black pills, ring stats, photo-on-top result sheet) in light; Pillowtalk-style night blues in dark.

Run: `npm install`, then `npm start` (Metro) and `./gradlew installDebug` (or Run in Android Studio). Release builds bundle the JS: `./gradlew assembleRelease`.
