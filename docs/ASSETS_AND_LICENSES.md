# Asset and license inventory

## Bundled assets

| Asset | Location | Source | License |
|---|---|---|---|
| Newsreader display regular, display medium and text medium | `app/src/main/res/font/newsreader_*.ttf` | Newsreader by Production Type ([github.com/productiontype/Newsreader](https://github.com/productiontype/Newsreader)). Static instances were made from the variable font with fontTools and subset to Latin. | SIL OFL 1.1; text in `app/src/main/assets/licenses/Newsreader-OFL.txt` and `docs/licenses/` |
| Inter regular, medium and semibold, and Inter Display semibold | `app/src/main/res/font/inter_*.ttf` | Inter by Rasmus Andersson ([github.com/rsms/inter](https://github.com/rsms/inter)). Static instances were made from the variable font with fontTools and subset to Latin. | SIL OFL 1.1; text in `app/src/main/assets/licenses/Inter-OFL.txt` |
| About 50 interface icons | `ui/icons/FormaIcons.kt` | Original outline icons drawn in code for this project | Project-owned |
| Illustrations (welcome, sprout, rest, complete, search, equipment, safety) and movement pose figures | `ui/illustrations/Illustrations.kt` | Original, drawn in code for this project | Project-owned |
| Launcher icon and notification icon | `res/drawable/ic_launcher_*.xml`, `ic_notification.xml`, `mipmap-anydpi/ic_launcher.xml` | Original **placeholder** vectors | Project-owned; replace before release |
| Exercise and program text | `core/engine/.../catalog/` | Written for this project; **draft**, not reviewed | Project-owned |

- **Not included:** no photos, videos, stock illustrations, icon packs or the Simmr reference's
  assets.
- **Simmr:** nothing from Simmr was copied. Its screenshots were not available, and only the written
  visual direction was used.
- **Reserved Font Names:** neither font's license declares one. The subset fonts keep their family
  names, and the license files are shipped in the APK.
- **In-app notices:** Help › Open-source notices lists the font and library licenses.

## Libraries (Android app)

| Library | License |
|---|---|
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 |
| AndroidX: Compose, Material 3, Activity, Lifecycle, Navigation, Room, DataStore, WorkManager and Core | Apache 2.0 |
| Google Play Billing Library | Android Software Development Kit License |

## Test and verification only (not shipped)

| Library | License |
|---|---|
| JUnit 4 | EPL 1.0 |
| Robolectric | MIT |
| AndroidX Test | Apache 2.0 |
| JetBrains Compose Multiplatform 1.5.12 (desktop harness) and Skiko | Apache 2.0 |
