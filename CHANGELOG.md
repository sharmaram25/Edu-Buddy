# Changelog

## 1.1.0

### New
- **Holidays & breaks.** Mark a day or a date range (festival break, exam week) as no-class. Class reminders are skipped, Today and the widget show the break, and attendance isn't asked for. Included in backups.
- **Search.** One search across tasks, subjects, teachers, events, holidays and expenses, with matches highlighted.
- New brand: Ink & Ember palette, new logo and app icon, notification icon and splash screen.

### Redesigned
- Every screen reworked around a calmer, monochrome layout: solid hero cards, hairline cards, neutral icons, underline tabs, tabular figures for times and money.
- Today: greeting header with search, "now / next" hero with live progress, classes and tasks grouped in single cards.
- Schedule: week strip with today marker, timeline-style class list, holiday banners.
- Focus: watch-face timer.
- Press feedback on cards and buttons, haptics when marking attendance or completing a task, animated checkboxes.

### Fixed
- Crash when tapping **Allow** for notifications during onboarding (also affected backup, restore and CSV export): the app was linking an outdated Fragment library.
- Possible crash with app lock on Android 8.x.
- Restoring a backup could crash if the chosen file became unreadable.
- File pickers no longer crash on devices without a document provider.
- Denying the notification prompt once no longer throws you into system settings.

### Technical
- Database v2 with an explicit migration (no data loss on update).
- Optional release signing via `keystore.properties`.
- GitHub Actions workflow running tests and building the APK.

## 1.0.0
- First release: timetable, attendance, tasks, events, money, focus timer, reminders, widget, app lock, backup & restore.
