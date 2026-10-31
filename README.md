# calc

A simple, local-only calendar app for Android.

Built with Kotlin, Jetpack Compose (Material 3) and Room. Events live on the
device only — no accounts, no calendar permissions, no sync.

Status: v1.0.0 released — see the [releases page](https://github.com/sumon317/calc/releases), [CHANGELOG.md](CHANGELOG.md) and the release runbook in [RELEASING.md](RELEASING.md).

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

calc is free software licensed under the **GNU GPL v3** (or any later version
of your choice) — see [LICENSE](LICENSE). You may redistribute and modify it
under those terms; there is no warranty. Copyleft applies: derived versions
must remain free under the GPL.
