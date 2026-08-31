# BIG_PICTURE.md — Art of Focus (Android)

> Ported from the original web app's `BIG_PICTURE.md`. This is the product
> concept and user journey — the "why" behind the invariants in
> [INVARIANTS.md](INVARIANTS.md) and the feature breakdown in
> [MODULE_MAP.md](MODULE_MAP.md).

**One sentence:** every completed focus sitting reveals one piece of a hidden
painting; finished paintings become a permanent part of a personal museum.

## Product philosophy

- **Effort becomes an object** — progress is always a visible *thing* (a
  puzzle piece, a painting), not a number ticking up.
- **Ceremony over notification** — starting a session is a small ritual, not
  a bare timer start. Deliberate friction to make starting feel meaningful.
- **The app molds to the user** — Focus level (Beginner/Intermediate/
  Advanced) sets sensible defaults, never limits (I4).
- **Friction where quitting happens, ease where starting happens** —
  cancelling is deliberately slow (I9); starting is one tap.
- **Teach invisibly** — the UI tour is met entirely by empty states, never an
  upfront overlay to skip.

## Core user journey

```
First launch -> Introduction (skippable slides on premise only)
  -> Profile setup: username, long-term goal, focus level (all optional)
  -> Sign up/log in offered later in Profile; never required
Focus tab (home)
  -> Press Start -> painting assigned, covered by mosaic
  -> Focus Setup: choose Total Focus, then Single Sitting (two steps)
  -> Stopwatch runs (wall-clock derived, I7)
  Sitting completes -> piece revealed immediately -> "Continue or Take Rest?" (60s countdown, I10)
      -> Continue -> next sitting
      -> Take Rest -> rest timer (own clock, I11) -> notification -> next sitting
  All pieces revealed -> painting 100% complete -> celebration, stats saved
      -> painting moves permanently into Museum (I12)
Museum / Profile / Friends give the effort a longer life.
```

## The four tabs

| Tab | Owns |
|---|---|
| **Focus** (home) | Session setup, stopwatch, rest, puzzle reveal, completion |
| **Museum** | Every completed painting, browsable by wall and by timeline |
| **Profile** | Identity, focus level, long-term goal, settings, statistics, achievements |
| **Friends** | Friend requests, groups, leaderboards based on focus hours/streak |

## Core systems (maps to `domain/` packages)

- **Focus System** — `domain/focus/` (Durations, Session, Puzzle, Artwork, FocusLevel)
- **Museum** — `domain/museum/`
- **Profile & Statistics** — `domain/profile/`
- **Achievements** — `domain/achievements/`
- **Friends** — `domain/friends/`
- **Notifications** — `domain/notifications/` + `notifications/` (WorkManager)
- **Onboarding** — `domain/onboarding/`
- **Account (Supabase sync)** — `domain/account/`

## Why native, not a WebView wrapper

The original web app is a React/TypeScript SPA with an optional Supabase
backend, designed to work fully offline with `localStorage` as source of
truth. This Android app is a **from-scratch Kotlin + Jetpack Compose port**
of the product's rules and UX — not a wrapped copy. It reuses:

- `supabase/schema.sql` **as-is** for the Friends backend (same tables, RLS
  policies, and RPC functions — no redesign).
- The exact algorithms in the web app's `domain/*.ts` files, ported line-by-
  concept into Kotlin with the same invariant tests re-derived as JUnit
  tests.

It does **not** reuse any TypeScript/React code, CSS, or the web app's
`localStorage` schema — those are reference material only. Local-first
persistence is reimplemented with Room (Museum) and DataStore Preferences
(everything else), mirroring the *behavior* of `state/*.ts` (validate on
read, discard invalid state, never repair it) rather than its storage format.

## Current build state (this repo)

Built and verified live on an emulator:
- App shell (4-tab bottom navigation)
- Focus: full core loop (setup -> timer -> puzzle reveal -> rest -> completion),
  including the I9 cancel-friction countdown and I7 wall-clock persistence
  (verified across app reinstall and a full emulator restart)
- Museum: Wall and Timeline views
- Profile: Stats, Achievements, Settings (including the I4 focus-level editor)
- Notifications: WorkManager-backed rest-end/daily-call/streak-risk workers

Not yet built:
- Friends module + Supabase wiring (blocked on a configured Supabase project;
  the domain logic in `domain/friends/` is complete and tested)
- Onboarding screen (domain logic in `domain/onboarding/` is complete; no UI
  yet — the app currently opens straight to the Focus tab)
- A distinct two-section landscape layout for the Focus screen (I8)
- Focus environment audio (lo-fi/ambient/binaural/silent) — not built in the
  source web app either (F6)
