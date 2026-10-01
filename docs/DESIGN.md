# Design system notes

## Visual direction

The design follows the brief's description of the Simmr reference:

- black backgrounds;
- warm cream text;
- serif page titles;
- charcoal cards with broad corners;
- pill controls;
- pastel category cards;
- cream primary buttons;
- simple outline illustrations.

**Note:** the brief mentions attached Simmr screenshots, but no images reached this project. The
look was built from the written description and colour tokens only. Compare it with the reference
and adjust tokens in `ui/theme/Theme.kt` and `ui/theme/Dimens.kt` as needed.

## Colour tokens

### Dark (default)

| Token | Value |
|---|---|
| background | `#000000` |
| surface (cards) | `#211F1A` |
| surfaceSecondary | `#191814` |
| border | `#343129` |
| text | `#F4EEE4` |
| textSecondary | `#B6B0A6` |
| textMuted | `#918B82` |
| accent | `#FF713D` |
| danger | `#FF7275` |

### Light

| Token | Value |
|---|---|
| background | `#F6F2EA` |
| surface (cards) | `#FFFCF6` |
| surfaceSecondary | `#EEE8DD` |
| border | `#D9D0C1` |
| text | `#1C1A16` |
| textSecondary | `#5A544B` |
| textMuted | `#6B6358` |
| accentText | `#B4441A` |
| danger | `#B3261E` |

### Theme setting

The setting offers **System**, **Light** and **Dark**, stored in preferences. Dark is the default
appearance when the system is dark.

### Light-theme adjustments

- Orange is used for fills and icons. Orange **text** uses a darker tone so it stays readable.
- Pastel cards get a thin border, because pastel on ivory has low contrast.

## Contrast (WCAG 2.x)

Body text needs at least 4.5:1. Every token below passes on every surface it is used on.

| Theme | Foreground | on background | on surface | on surfaceSecondary |
|---|---|---|---|---|
| Dark | text #F4EEE4 | 18.2 | 14.3 | 15.4 |
| Dark | textSecondary #B6B0A6 | 9.7 | 7.6 | 8.2 |
| Dark | textMuted #918B82 | 6.2 | 4.9 | 5.3 |
| Dark | accent #FF713D | 7.7 | 6.0 | 6.5 |
| Dark | danger #FF7275 | 7.9 | 6.2 | 6.7 |
| Light | text #1C1A16 | 15.6 | 17.0 | 14.2 |
| Light | textSecondary #5A544B | 6.7 | 7.3 | 6.1 |
| Light | textMuted #6B6358 | 5.3 | 5.8 | 4.9 |
| Light | accentText #B4441A | 5.0 | 5.4 | 4.6 |
| Light | danger #B3261E | 5.9 | 6.4 | 5.4 |

**Primary buttons:**

- Dark theme: #191814 on #F4EEE4 is 15.4:1.
- Light theme: #F6F2EA on #1C1A16 is 15.6:1.

**Pastel cards** use the same pastels in both themes:

| Pastel | Content text | Secondary text | Card vs black background |
|---|---|---|---|
| blue #A8BDD6 | 8.6 | 5.6 | 10.9 |
| mint #AFD8C6 | 9.9 | 6.0 | 13.5 |
| green #C3D7A2 | 9.6 | 5.8 | 13.6 |
| peach #F0C3A4 | 9.2 | 5.7 | 13.0 |
| lavender #C8BDE3 | 8.9 | 5.6 | 11.8 |
| butter #E8D695 | 9.8 | 5.9 | 14.5 |
| rose #E6B5BC | 8.5 | 5.4 | 11.7 |

## Typography

| Use | Font |
|---|---|
| Page titles | Newsreader Display, regular, 40 sp |
| Card titles | Newsreader Text, medium |
| Interface text | Inter: regular, medium, semibold |
| Large numbers (timers, weights) | Inter Display semibold with tabular figures |

- All fonts are bundled, so the app works offline. They are static subsets made from the
  variable fonts, licensed under SIL OFL 1.1.
- Text follows the system font scale. A `maxScale` cap applies only in two places:
  - labels inside fixed-size containers, such as bottom navigation and pill buttons;
  - very large timer numbers.

  In both cases the cap keeps them inside their containers at 200% font size.

## Layout and accessibility

- **Screen padding:** 20 dp, or 16 dp on narrow screens and at 175% font size or more.
- **Compact layout:** at 360 dp width, or at 150% font size or more, the layout switches to compact
  arrangements, such as a single column or stacked stats.
- **Touch targets:** at least 48 dp.
- **Corners:** 24–28 dp on cards and fully rounded on pills.
- **Motion:** the only motion is short screen fades and the Material bottom sheets. There is no
  decorative or looping animation.
  - When the system's "Remove animations" setting is on, the screen fades are turned off.
  - The bottom sheets follow the system animation scale.
- **Live regions:** timers announce only meaningful moments, such as "Time's up", not every
  second.
- **Icons:** about 50 original outline icons, drawn in code as vector paths in `ui/icons`.
- **Illustrations:** original outline illustrations for welcome, empty states, rest, completion,
  search, equipment and safety, also drawn in code in `ui/illustrations`.
- **Demonstrations:** an outline pose figure per movement pattern stands in for demonstrations,
  and is labelled as a placeholder.
