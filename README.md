# calendar

A full-fledged, privacy-respecting calendar app for Android connecting directly to your device's local calendar.

Built with Kotlin and Jetpack Compose (Material 3). Connects directly to the Android System Calendar Provider (`CalendarContract`), providing seamless two-way live synchronization: changes made in the app are instantly reflected in your device's local calendar (and any other calendar app), and changes made anywhere on the device are immediately reflected here via `ContentObserver`.

## Features

- **Full Local Calendar Integration**: Connects directly to Android's built-in `CalendarContract` provider (`ACCOUNT_TYPE_LOCAL` and all device calendars).
- **Google Calendar Account Integration**: Connect and sign into your Google account directly from the app. Automatically detects connected Google accounts, verifies that calendar synchronization is active, and supports adding new Google accounts via standard Android account flows.
- **Two-Way Live Sync**: Changes made in this app sync instantly to your Google Calendar and other local calendar apps. Edits, deletions, or events added anywhere (Google Calendar on the web, other devices, or local apps) appear immediately in real time via `ContentObserver`.
- **Expedited Cloud Sync**: Adding, updating, or deleting an event automatically requests an expedited cloud sync via `ContentResolver.requestSync()` for connected Google accounts, plus a dedicated "Sync Now" action.
- **Recurring & One-Off Events**: Powered by `CalendarContract.Instances` query expansion so recurring events (birthdays, weekly routines, meetings) render accurately on every month and day.
- **Rich Event Management**: Create, edit, and delete events with title, description/notes, location, start/end dates and times, all-day toggle, calendar selector (clearly identifying Google vs. local calendars), and reminder alerts.
- **Calendar Filtering**: Filter by specific calendar or view all calendars at once. Enable or disable individual calendars.
- **Customizable Themes & OLED Mode**: 7 designer color palettes (Material You, Nordic Frost, Catppuccin, Everforest, Tokyo Night, Rosé Pine, Warm Paper) and 4 modes (System, Light, Dark, OLED Pitch Black).
- **Graceful Permissions**: Clear in-app permission banner and runtime permission handling for `READ_CALENDAR` and `WRITE_CALENDAR`.

Status: v1.1.2-beta released — see the [releases page](https://github.com/sumon317/calendar/releases), [CHANGELOG.md](CHANGELOG.md) and the release runbook in [RELEASING.md](RELEASING.md).

## Development

Build and test from the command line (JDK 17 required):

```
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

CI runs lint, unit tests, and debug + release assembly on every push and PR
to `main`. Pushing a `v*` tag triggers the release pipeline, which rebuilds,
signs, and attaches the APK to a GitHub release.

## License

Copyright (C) 2026 sumon317

calendar is free software licensed under the **GNU GPL v3** (or any later version
of your choice) — see [LICENSE](LICENSE). You may redistribute and modify it
under those terms; there is no warranty. Copyleft applies: derived versions
must remain free under the GPL.
