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

A painting has one of **three sources**, recorded on `Painting.source`
(`ArtSource`): a **community painting** (public-domain art, the default), **one
of the user's own pictures**, or a **generated gradient**. `imageRef` carries
the image for the first two and is null for the third.

The source is stored rather than inferred. It used to be read off
`imageRef != null`, which worked only while "has an image" and "is the user's
photo" meant the same thing; community art broke that, and the Pictures library
would have claimed to own downloaded museum art. The earlier note that
"swapping in real art only touches `PALETTES`" applied to a global swap;
per-painting art needed the model to carry state not derivable from the seed.

**Gradients are the fallback, not the offer.** They appear only when no
community art is cached and the device is offline — a first run with no
signal. Everywhere else the user gets real art.

Decisions worth keeping:
- **The card is always 4:5**, in a session and in the Museum alike, and a
  photo fills it (`ContentScale.Crop`) rather than fitting inside it. Letting
  the frame take the picture's aspect ratio was tried and reverted: a wide
  photo turned the card into a letterbox and crushed the tiles into strips.
  The crop is display-only — the stored file is never altered.
- **Pictures are never consumed.** One picture can back any number of
  paintings; abandoning a painting leaves it in the library.
- **A picture a painting depends on cannot be deleted.** The rule is
  `domain/focus/PhotoUsage.kt`, covering both completed paintings (I12) and
  the active one (I7); the library shows those tiles as "In your Museum"
  instead of offering removal. Locking is about deletion only — a locked
  picture stays selectable.
- **Photos are copied into app-private storage** at import, never referenced
  by gallery URI (revocable grant, does not survive reboot) — I12.
- **Community paintings sync; the user's photos deliberately do not.** The
  `paintings` table carries `source`, `credit` and `image_url`, and `image_url`
  is only ever a PUBLIC url — the museum's own hosted image. A private photo is
  never uploaded, so a photo-backed painting restored on another device has its
  title and times but no picture, and says so rather than showing a broken
  frame. Uploading private photos is a product decision, not an omission.
- **Coil** (`io.coil-kt:coil-compose`) is the app's image loader.
- **Community art never enters the timer path.** `CommunityArtRepository.peek`
  reads the local cache only, so a sitting starts instantly and offline (I7).
  `topUp` is the sole network call and runs at launch and after a painting
  completes — never during a session. Images are downloaded into app-private
  storage for the same reason photos are: a painting whose artwork is a remote
  URL is one outage away from an empty frame (I12).
- **The source is the Metropolitan Museum of Art** (`data/remote/CommunityArtApi.kt`),
  chosen for needing no key or account and for marking public-domain work
  explicitly. Artist and year are credited regardless, because crediting an
  artist is right even when the licence does not require it. That file is the
  only one that knows the API exists.

  The Art Institute of Chicago was tried first and dropped: its metadata API is
  fine, but its IIIF image host sits behind Cloudflare and answers an image
  request with a bot-challenge HTML page. Twelve of those were cached as `.jpg`
  and would have rendered as blank frames. Downloads now verify the image magic
  number and the cache prunes anything that is not really an image, so a cache
  poisoned by a bad source heals itself.
- **The cache is not the Museum.** `CommunityArtCache` is a disposable JSON
  index plus image files; losing it costs a re-download. It deliberately sits
  outside the Room database, where I12 and a migration would apply. A used
  painting is marked, never deleted — a completed painting still points at it.
- Friend gifts remain planned and unbuilt (accepted unseen, unveiled by
  focusing — needs Supabase Storage and the D6 auth work). Community
  *contribution* (the user submitting their own work) is likewise unbuilt: the
  Pictures tab states the rules it will follow, and offers no upload.

Room is at **schema version 5**; `data/local/Migrations.kt` holds all four
migrations (v2 adds the nullable `photoUri` column; v3 adds `source` and
`credit`, back-filling existing rows to `OWN_PHOTO` where an image is present
and `GENERATED` otherwise; v4 adds `remoteUrl` so a community painting can be
rendered on another device; v5 adds the unique index on `paintingKey` that
should have existed from v1 — without it `OnConflictStrategy.IGNORE` had no
constraint to hit, so `recordCompletion` was never actually idempotent and the
same painting could be stored twice). `fallbackToDestructiveMigration` must never be used
— it drops the museum (I12).

Every migration is covered by an instrumented test
(`app/src/androidTest/.../MigrationTest.kt`) asserting each painting survives
the whole v1 -> v4 path. Those tests were themselves checked by deliberately
sabotaging a migration to drop the table and confirming they failed.

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
- **F10.5** notification prefs — **built.** Four toggles in Profile >
  Settings, plus the POST_NOTIFICATIONS request and a delivery self-test that
  reports why a notification did not arrive (`NotificationSection` in
  `ui/profile/ProfileScreen.kt`).
- **F10.6** account auth + clear local cache — **built.** Google sign-in,
  sign-out, and a separately-gated "Clear local data".
- **F10.7** statistics — `domain/profile/Statistics.kt`, verified live
  (finished paintings only, screen states this scope).

Files: `domain/profile/{Profile,Statistics}.kt`, `domain/achievements/Achievements.kt`,
`data/repository/ProfileRepository.kt`, `ui/profile/{ProfileViewModel,ProfileScreen}.kt`.

---

## M5 — Friends & Groups

Status: **built.** Domain, Supabase wiring and UI are all in place against
the live project (`supabase/schema.sql` reused as-is — see BIG_PICTURE.md).

- `domain/friends/Handle.kt` — handle validation (length, charset, reserved
  names), exact-match search semantics.
- `domain/friends/Friends.kt` — leaderboard ranking (I13's four public
  fields only), deterministic tie-breaking.
- `domain/friends/Groups.kt` — invite code generation/formatting (excludes
  ambiguous characters), delegates ranking to the same function as the
  friends board.

`data/repository/{FriendsRepository,GroupsRepository}.kt` call the schema's
RPCs (`find_by_handle`, `claim_handle`, `pending_requests`, `create_group`,
`join_group`, `group_standings`) exactly as the web client does — no backend
redesign. `ui/friends/{FriendsViewModel,FriendsScreen}.kt` is the UI; signed
out it says so plainly rather than showing an empty board.

Note the package is `data/repository/`, not the `data/remote/` this section
once predicted — remote calls sit alongside the local repositories rather than
in a separate layer.

---

## M6 — Onboarding & Auth

Status: **built.** `domain/onboarding/Onboarding.kt` has the three
premise-only slides and the `SLIDES -> SETUP -> DONE` state machine (skip jumps
straight to `DONE`, matching F12.2); `OnboardingRepository` persists it,
`ui/onboarding/OnboardingScreen.kt` renders it, and `AppRoot` in
`MainActivity.kt` gates the shell on `isComplete(...)` — the introduction is
rendered *instead of* the shell, so no session ViewModel or ticker runs behind
the slides.

Auth is **Google only** — no email/password and no magic link. An earlier
version of this section described a magic-link state machine; that approach was
replaced. `ui/auth/GoogleSignIn.kt` uses Credential Manager and passes the
hashed nonce to Google while giving Supabase the raw one;
`data/repository/AuthRepository.kt` exchanges the ID token.
`domain/account/Account.kt` keeps the account-status and
`SyncStatus`/`hasUnsyncedWork` logic.

---

## M7 — Achievements & Streaks

Status: **built and verified live.** `domain/achievements/Achievements.kt` —
streak milestones (3/7/14/30/90 days, reading `longestStreak` per I15),
focus-hour milestones (1/10/50/100/500h), completion milestones
(1/5/10/25 paintings). Rendered in Profile's Achievements sub-tab, including
"next achievement" (highest completion fraction among the unearned).

---

## M8 — Notifications

Status: **built and live-tested on a device.** `notifications/NotificationChannels.kt`,
`AppNotifier.kt`, `SessionService.kt`, `RestEndReceiver.kt`,
`BootRescheduleReceiver.kt`, `DailyReminderWorker.kt`, `StreakRiskWorker.kt`,
`AppWorkerFactory.kt`, `NotificationScheduler.kt`, `DeliveryMessages.kt`, plus
`domain/notifications/OemGuidance.kt`, `domain/focus/SessionNotification.kt` and
`ui/common/NotificationPermission.kt`.

**A correctly scheduled alarm was not enough, and this is the heart of M8.**
Android freezes and then kills a backgrounded process with no visible ongoing
work, and an OEM task cleaner force-stopping the app **cancels its pending
alarms** — so the exact alarm below could be armed perfectly and still never
fire. Nothing was left running to notice a *sitting* had finished either, so
rest end was the only event with any background path.

`SessionService` is the answer: a foreground service holding an ongoing,
silent, non-dismissible notification with the live countdown for the length of a
session, started from user taps in `FocusViewModel` (Begin / Take Rest /
Continue) because API 31+ forbids a background start. It is the only mechanism
Android offers for declaring work ongoing.

**Its containment rule is load-bearing: the service reads session state and
never writes it.** Every number shown is derived from wall-clock timestamps via
`domain/focus/SessionNotification.kt`; the ViewModel's ticker stays the only
thing that advances a session, so I7 and I11 cannot be reached from here.
`SessionServiceTest` pins this by running the service over a sitting whose time
is already up and asserting the persisted session is unchanged. The service also
posts the sitting-complete notification, which could not exist before it.

**Rest end is an exact AlarmManager alarm, not a worker.** WorkManager's
`setInitialDelay` is explicitly inexact and can slip minutes under Doze, which
for a five-minute rest is the whole feature. `RestEndReceiver` is the target;
it is keyed on `sittingIndex`, which survives the RESTING -> REST_COMPLETE
transition. An earlier version keyed on `restStartedAt`, which `completeRest`
zeroes — so with the app open the check compared 0 against a real timestamp and
the notification was silently dropped. It fired only when the app was closed.
Verified by A/B against the pre-fix build on one device.

The alarm is re-armed in four situations, each of which used to lose it
permanently. `BootRescheduleReceiver` handles reboot, **app update**
(`MY_PACKAGE_REPLACED` — an update cancels alarms exactly as a reboot does, and
this was unhandled, so shipping a release to a user mid-rest dropped their
notification) and clock/timezone changes (the alarm is `RTC_WAKEUP`, so moving
the clock moves when it is due). `NotificationScheduler.reconcileRestEnd` also
runs on every app launch from `ArtOfFocusApp`, covering a force-stop and a
revoked exact-alarm permission — neither of which broadcasts anything.
`RestEndAlarmTest` pins the guards: it re-arms a rest in progress and nothing
else. `RestEndReceiver` now also holds a short `PARTIAL_WAKE_LOCK` across its two
DataStore reads, since `goAsync` keeps the receiver alive but not the CPU.

The daily and streak kinds ride on WorkManager, which is fine for a daily nudge.

**The permission gap is closed.** Every notification is silently dropped when
POST_NOTIFICATIONS has not been granted, and the request used to live *only* in
Profile — so a user who installed the app, ran a session and took a rest without
ever opening that tab got nothing at all, silently. It is now asked in three
places through the shared `ui/common/NotificationPermission.kt`: at the end of
onboarding (catches everyone), when first choosing a rest (where the reason is
concrete and the grant rate highest), and still in Profile for changing it
later. That helper also handles the case most implementations miss — after two
declines Android never shows the dialog again, so a button wired straight to
`launch` becomes silently dead; it detects that and opens notification settings
instead.

Profile's delivery self-test now also reports exact-alarm status with a button to
fix it, offers a route to notification settings when a notification is blocked
rather than merely ungranted, and gets its OEM battery advice from the pure
`domain/notifications/OemGuidance.kt`. That advice was hardcoded for Vivo/iQOO,
the phone the problem was first reported on, which meant a Xiaomi user was told
to find a menu that does not exist on their phone.

The small icon is `res/drawable/ic_notification.xml`, a flat monochrome mask.
It was `android.R.drawable.ic_dialog_info` — a framework dialog asset, which
stricter OEM skins can refuse to render at all.

Daily/streak workers explicitly refuse to fire while a sitting is `RUNNING`
(I7). Achievement notifications fire from `FocusViewModel.finishPainting` by
diffing earned-before against earned-after (`domain/achievements/NewlyEarned.kt`)
— `notifyAchievement` previously had no caller at all, so the "Achievement
earned" toggle controlled nothing while the sign-in copy promised it would.

---

## Testing

`app/src/test/` mirrors the source web app's `*.test.ts` invariant suites as
JUnit tests — 148 as of the last full run, plus instrumented tests in
`app/src/androidTest` (migrations, additive merge, the rest-end alarm guards,
and the session service's read-only containment), covering I1 through I15's
domain-layer logic (`DurationsTest`, `SessionTest`, `PuzzleTest`,
`MuseumTest`, `StatisticsTest`, `AchievementsTest`, `HandleTest`,
`FriendsTest`). Run with:

```bash
./gradlew testDebugUnitTest
```
