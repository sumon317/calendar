# Changelog

All notable changes to calc are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning is
semver-ish, and every entry corresponds to a GitHub release.

## [Unreleased]

### Added

- Outlined ring marking "today" on the grid, so the current date is
  identifiable at a glance even when another day is selected.
- The month name and year in the header are now separately tappable and
  open scroll-and-tap lists (months of the shown year; years 1900–2100).
- A "Today" button that appears whenever the calendar is not showing
  today; tapping it returns to today's month and selects today's date.

## [1.0.0] — 2026-10-31

First public release: a simple, local-only calendar app for Android.

### Added

- Month grid with a today marker, day selection, per-day event dots and
  month navigation (previous/next), following the device locale's week start
  (Sun-start for `en_US`).
- Persistent local events via Room: title + date + optional time, with no
  time meaning all-day. Data lives on-device only — no accounts, no sync,
  no calendar permissions.
- Full add/edit/delete flow from a single FAB-triggered dialog: tap event
  rows to edit, Material date + time pickers (honouring the device 12/24-h
  setting), Save guarded until the title is non-empty.
- Day section under the grid lists the selected day's events as
  click-to-edit cards, with an empty-state hint.
- Material 3 UI with dynamic colour (Android 12+), full dark- and
  light-mode support, and content descriptions for accessibility.

### Internal

- Kotlin 2.1 / Jetpack Compose (Material 3) / Room 2.7 / AGP 8.10 toolchain.
- Unit-tested month-grid logic (week padding, locale week-start handling).
- CI pipeline on GitHub Actions: Gradle wrapper validation, Android lint,
  unit tests and debug + release assembly on every push and PR to `main`.
- Tag-triggered release pipeline: lint + tests gate, release-key signing via
  encrypted repo secrets, GitHub Release published with the attached APK and
  notes taken from the annotated tag.

### Licence

- GNU GPL v3 (or later) — see `LICENSE`.
