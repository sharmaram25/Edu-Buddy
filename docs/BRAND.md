# EduBuddy brand

## Idea

A student's day is a sequence of blocks: classes, deadlines, breaks. The mark is a **day bracket** (an E without its middle arm) holding a single **ember dot**: *now*. Everything else in the interface is quiet so that "now" stands out.

<img src="logo.svg" width="96" alt="EduBuddy mark"/>

## Colour

| Token | Light | Dark | Use |
|---|---|---|---|
| Ink | `#131312` | `#F1EFEA` | Text, primary buttons, the hero card |
| Paper | `#F6F5F1` | `#0C0C0B` | App background |
| Card | `#FFFFFF` | `#161615` | Cards, sheets, dialogs |
| Hairline | `#E2E0DA` | `#272725` | Card edges, dividers |
| Muted | `#6E6C66` | `#9F9D97` | Secondary text, icons |
| **Ember** | `#F2542D` | `#FF7A55` | "Now", today, the logo dot. Never decorative. |

Status colours (success `#1F8A5B`, warning `#B7791F`, danger `#C4321F`) only appear when something needs attention.

Subject colours are eight hues tuned to similar lightness so a timetable reads as one palette: Indigo, Terracotta, Teal, Rose, Sky, Ochre, Plum, Moss. Their order is stored in user data and must not change.

## Type

Plus Jakarta Sans (variable). Headlines at weight 600 with slight negative tracking; body at 400. Tabular figures everywhere so times and amounts align and countdowns don't jitter. Small uppercase labels (11 sp, +0.6 tracking) act as eyebrows above titles.

## Shape and surface

- Cards: white on paper, 18 dp corners, 1 dp hairline, no shadow.
- One ink **hero** per screen at most (Today's next class, the month's spending, overall attendance).
- Icons are plain glyphs in the muted colour; colour goes to meaning, not decoration.
- Primary actions are ink capsules; secondary actions are outlined or text.

## Motion

Short and physical: cards press down slightly, checkboxes pop when completed, tabs slide an underline. Haptic ticks confirm attendance marks and completed tasks. Nothing loops or animates on its own.

## Assets

- App icon: `app/src/main/res/drawable/ic_launcher_*.xml` (adaptive, with monochrome layer for themed icons)
- Notification icon: `ic_stat_edubuddy.xml`
- In-app mark: `BrandMark` / `AppLogo` / `Wordmark` in `ui/common/Components.kt`
- Vector for docs and store listings: `docs/logo.svg`
