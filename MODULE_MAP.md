# MODULE_MAP.md — Art of Focus (Android)

> Ported from the original web app's `MODULE_MAP.md`, adapted to this
> repo's actual package layout. Each module lists its Kotlin packages and
> current build status. See [BIG_PICTURE.md](BIG_PICTURE.md) for the product
> concept and [INVARIANTS.md](INVARIANTS.md) for the hard rules each module
> must not violate.

## Architecture pattern

MVVM, mirroring the web app's `domain/` + `state/` + `components/` split:

```
domain/<feature>/       pure Kotlin, no Android framework deps, unit-tested
data/local/             Room entities/DAOs, DataStore key definitions
data/repository/        one repository per state concern — owns its keys,
                         validates on read, discards invalid state (never repairs)
ui/<feature>/           ViewModel (StateFlow<UiState>) + Compose Screen
notifications/          WorkManager workers + scheduling (M8)
```

No dependency-injection framework — `AppContainer` is a small hand-rolled
container of lazily-created repositories, held by `ArtOfFocusApp` and passed
into ViewModel factories.

---

## M0 — App Shell

Status: **built**. Four-tab bottom navigation (Focus/Museum/Profile/Friends)
in `MainActivity.kt`'s `AppShell` composable. No business logic — pure
composition + a `Lifecycle` observer wiring `ON_STOP`/`ON_START` to the Focus
ViewModel's away-time detection (I7).

Not yet built: a distinct landscape layout (I8) — currently relies on
Compose's default reflow.

---

## M1 — Focus System (owns I1, I2)

Status: **built and verified live on an emulator.**

- **F1 Session Setup** — [FocusScreen.kt](app/src/main/java/com/artoffocus/app/ui/focus/FocusScreen.kt)'s
  `SetupContent`: two-step Total-then-Sitting selection, live piece-count
  preview, Start button. Blocked while a painting is active (I6).
- **F2 Focus Timer** — wall-clock-derived elapsed time (I7), merged caption
  "1 of 4 complete · 4m / 2h" via `describeSessionProgress`.
- **F3 Session Controls** — Pause/Resume; Cancel with a visible 5s countdown
  + confirmation (I9), verified live.
- **F4 Rest System** — rest duration options (5–30m step 5, default 10), own
  clock, 60s continue-lock (D30).
- **F5 Session Completion** — "Continue or Take Rest?" with a 60s
  auto-continue countdown (I10), suppressed on the final sitting.
- **F6 Focus Environment** — **not built** (matches the source web app —
  lo-fi/ambient/binaural/silent audio was never implemented there either).
- **F7 Interruption Recovery** — F7.2 (resume from saved state) is built and
  verified across process death and a full emulator restart. F7.3 (a
  minimized pop-up stopwatch) is not built.

Files: `domain/focus/{Durations,Session,Puzzle,Artwork,FocusLevel}.kt`,
`data/repository/{FocusSetupRepository,SessionRepository,ArtworkRepository}.kt`,
`ui/focus/{FocusViewModel,FocusScreen}.kt`.

---

## M2 — Artwork & Puzzle System

Status: **built** (assignment + division + reveal + completion). No cabinet-
opening ceremony animation (the source web app built one, then reverted it —
see its MODULE_MAP D47; not attempted here either).

A painting is either a **generated gradient** (the deterministic seeded scheme
shared with the source app, `domain/focus/Artwork.kt`) or **one of the user's
own pictures**. `Painting.imageRef` distinguishes them: null means gradient.
The earlier note that "swapping in real art only touches `PALETTES`" applied
to a global swap; per-painting user art needed the model to carry state that
is not derivable from the seed.

Decisions worth keeping:
- **Two contexts, two crop rules.** During a session the frame takes the
  picture's own aspect ratio (nothing cropped, no tile over empty ground);
  in the Museum cards stay a uniform 4:5 and the photo is centre-cropped.
  The crop is display-only — the stored file is never altered.
- **Pictures are never consumed.** One picture can back any number of
  paintings; abandoning a painting leaves it in the library.
- **Photos are copied into app-private storage** at import, never referenced
  by gallery URI (revocable grant, does not survive reboot) — I12.
- **Photo paintings are not synced.** The `paintings` table carries no image,
  so a pushed photo painting would read back elsewhere as a gradient.
- **Coil** (`io.coil-kt:coil-compose`) is the app's image loader.
- Two further painting sources are planned and deliberately not built:
  community paintings (needs an upload pipeline and moderation) and friend
  gifts (accepted unseen, unveiled by focusing — needs Supabase Storage and
  the D6 auth work).

Room is at **schema version 2**; `data/local/Migrations.kt` holds the first
migration (adds the nullable `photoUri` column). `fallbackToDestructiveMigration`
must never be used — it drops the museum (I12).

---

## M3 — Museum

Status: **built.** `domain/museum/Museum.kt` (pure — key/stats/grouping
logic) + `data/repository/MuseumRepository.kt` (Room-backed, per-record fault
tolerance on read) + `ui/museum/{MuseumViewModel,MuseumScreen}.kt` (Wall grid
view + Timeline grouped-by-month view, verified live).

Not built: a "support the artist" screen (needs real artist/payment
integration — not built in the source app either).

---

## M4 — Profile & Settings

Status: **built.** `ProfileScreen` composes three internal sub-tabs
(Stats/Achievements/Settings — unrelated to the four main app tabs), all
verified live:

- **F10.1** username/goal (both optional) — `domain/profile/Profile.kt`.
- **F10.2** editable focus level, mid-painting-safe (I4) —
  `ProfileViewModel.saveLevel`.
- **F10.3/F10.4** studio/interface customization, default focus environment —
  **not built** (blocked on F6, same as the source app).
- **F10.5** notification prefs — domain (`domain/notifications/`) and storage
  (`NotificationPrefsRepository`) exist; no Settings UI toggle yet (currently
  only editable by changing stored defaults).
- **F10.6** account auth + clear local cache — `domain/account/Account.kt`
  and `AccountRepository` exist; no UI yet (pending Supabase wiring).
- **F10.7** statistics — `domain/profile/Statistics.kt`, verified live
  (finished paintings only, screen states this scope).

Files: `domain/profile/{Profile,Statistics}.kt`, `domain/achievements/Achievements.kt`,
`data/repository/ProfileRepository.kt`, `ui/profile/{ProfileViewModel,ProfileScreen}.kt`.

---

## M5 — Friends & Groups

Status: **domain logic complete and tested; UI and Supabase wiring pending**
a configured Supabase project (`supabase/schema.sql` reused as-is — see
BIG_PICTURE.md).

- `domain/friends/Handle.kt` — handle validation (length, charset, reserved
  names), exact-match search semantics.
- `domain/friends/Friends.kt` — leaderboard ranking (I13's four public
  fields only), deterministic tie-breaking.
- `domain/friends/Groups.kt` — invite code generation/formatting (excludes
  ambiguous characters), delegates ranking to the same function as the
  friends board.

Once Supabase credentials are available: a `data/remote/` package wrapping
`io.github.jan-tennert.supabase` (already in `build.gradle.kts`) will call
the schema's RPCs (`find_by_handle`, `claim_handle`, `pending_requests`,
`create_group`, `join_group`, `group_standings`) exactly as the web client
does — no backend redesign.

---

## M6 — Onboarding & Auth

Status: **domain logic complete; UI not yet built.** `domain/onboarding/Onboarding.kt`
has the three premise-only slides and the `SLIDES -> SETUP -> DONE` state
machine (skip jumps straight to `DONE`, matching F12.2). `OnboardingRepository`
persists it. The app currently opens straight to the Focus tab without
gating on onboarding completion — wiring the actual slide UI and the
`hasCompletedOnboarding()` gate in `MainActivity` is a follow-up.

`domain/account/Account.kt` has the magic-link account-status state machine
and `SyncStatus`/`hasUnsyncedWork` logic, pending Supabase wiring (see M5).

---

## M7 — Achievements & Streaks

Status: **built and verified live.** `domain/achievements/Achievements.kt` —
streak milestones (3/7/14/30/90 days, reading `longestStreak` per I15),
focus-hour milestones (1/10/50/100/500h), completion milestones
(1/5/10/25 paintings). Rendered in Profile's Achievements sub-tab, including
"next achievement" (highest completion fraction among the unearned).

---

## M8 — Notifications

Status: **built**, not yet live-tested (would require waiting out a real
rest period or daily/streak interval). `notifications/NotificationChannels.kt`,
`AppNotifier.kt`, `RestEndWorker.kt`, `DailyReminderWorker.kt`,
`StreakRiskWorker.kt`, `AppWorkerFactory.kt`, `NotificationScheduler.kt`.

Unlike the source web app, there is no reliable/best-effort split and no
platform-support detector — WorkManager delivers all four kinds reliably
regardless of app or process state. See INVARIANTS.md's I7 native-specific
note.

`RestEndWorker` is scheduled the instant rest starts (`FocusViewModel.chooseRest`)
and re-validates that the same rest period is still active before posting,
so a stale notification can't appear after the user already continued or
cancelled. Daily/streak workers explicitly refuse to fire while a sitting is
`RUNNING` (I7).

---

## Testing

`app/src/test/` mirrors the source web app's `*.test.ts` invariant suites as
JUnit tests — 78 tests as of the last full run, covering I1 through I15's
domain-layer logic (`DurationsTest`, `SessionTest`, `PuzzleTest`,
`MuseumTest`, `StatisticsTest`, `AchievementsTest`, `HandleTest`,
`FriendsTest`). Run with:

```bash
./gradlew testDebugUnitTest
```
