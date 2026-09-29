# Art of Focus (Android)

A native Android port of [Art of Focus](https://github.com/Mrpoopypantzz/art-of-focus1) —
turn deep work into art. Every completed focus sitting reveals one piece of a
hidden masterpiece.

Kotlin + Jetpack Compose, MVVM, local-first (Room + DataStore) — the app
works fully offline with no account. See [BIG_PICTURE.md](BIG_PICTURE.md) for
the product concept, [INVARIANTS.md](INVARIANTS.md) for the hard business
rules, and [MODULE_MAP.md](MODULE_MAP.md) for the full feature breakdown and
build status.

## Building

Requires JDK 17 and the Android SDK (compileSdk 36, minSdk 26).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`local.properties` (gitignored) must point `sdk.dir` at your Android SDK.

## Architecture

```
domain/<feature>/       pure Kotlin business rules, no Android deps, unit-tested
data/local/             Room entities/DAOs, DataStore key definitions
data/repository/        validates on read, discards invalid state, never repairs
ui/<feature>/           ViewModel (StateFlow) + Jetpack Compose screen
notifications/          foreground session service, exact rest-end alarm, daily/streak workers
```

Duration math, session state transitions, and puzzle-piece derivation live
**only** in `domain/` — if a screen or repository does arithmetic on
durations, that's a bug (see INVARIANTS.md I1/I2).

## Backend (optional)

The Friends module reuses [`supabase/schema.sql`](supabase/schema.sql) from
the source web app as-is — no backend redesign. Run it against a Supabase
project, then supply the project URL and anon key to enable Friends
(everything else works fully offline without it).
