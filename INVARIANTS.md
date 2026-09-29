# INVARIANTS.md — Art of Focus (Android)

> Ported from the original web app's `INVARIANTS.md`. These rules must remain
> true across every refactor and future feature — breaking one is a bug even
> if the code compiles and the screen looks right. If a new requirement
> contradicts an invariant here, stop and raise the conflict; do not silently
> change the rule.
>
> Each invariant names the Kotlin file(s) that own it. All of them live under
> `app/src/main/java/com/artoffocus/app/domain/` and have zero Android
> framework dependencies — they are plain, unit-testable Kotlin.

---

## I1 — The Duration Invariant

```
Total Focus = Single Sitting × Number of Sittings
Number of Sittings ∈ ℤ, ≥ 2
```

No rounding, no remainder sitting, no partial final sitting.

- The Sitting selector only offers exact divisors of the chosen Total
  (`sittingsFor` in [Durations.kt](app/src/main/java/com/artoffocus/app/domain/focus/Durations.kt)).
- A Total/Sitting pair loaded from [FocusSetupRepository](app/src/main/java/com/artoffocus/app/data/repository/FocusSetupRepository.kt)
  is re-validated (`isValidPair`) on every read. An invalid stored pair is
  **discarded, not repaired** — the repository falls back to the current
  level's default.
- Minimum 2 sittings.
- **Rest time is not focus time.** [Session.kt](app/src/main/java/com/artoffocus/app/domain/focus/Session.kt)'s
  `sessionProgress` reports zero live sitting time while `RESTING` /
  `REST_COMPLETE` — otherwise the frozen sitting length would be banked twice.

---

## I2 — One Sitting = One Puzzle Piece

```
Number of Puzzle Pieces = Total Focus ÷ Single Sitting
```

Piece count is **derived**, never stored, never chosen by the user.

- `layoutFor` in [Puzzle.kt](app/src/main/java/com/artoffocus/app/domain/focus/Puzzle.kt)
  arranges *n* tiles (presentation) — it must never re-derive *n*. `tileCount(layout)`
  is pinned by test to always equal the input piece count.
- The Focus setup screen's live division preview reads `pieceCount()` directly;
  it does not compute, cache, or create any session/painting state.
- **The Total/Sitting pair is frozen once a painting is active** ([ArtworkRepository](app/src/main/java/com/artoffocus/app/data/repository/ArtworkRepository.kt)'s
  `assignIfAbsent` refuses to replace a live painting — see I6).
- Piece counts are bounded above at `MAX_PIECES = 90`. `MIN_PIECES = 2` is
  retained conceptually for the puzzle layer but is **not** enforced by
  `sittingsFor` — only the ceiling is.

---

## I3 — Total Focus Comes From a Derived Set

Total Focus is never typed; it is chosen from **one shared ladder** for every
user. Focus level does not filter it — only sets the default landing position
(see I4). Exactly 20 candidates, no gaps (`TOTAL_CANDIDATES` in Durations.kt):

```
30m, 45m, 1h, 1h15, 1h30, 1h45, 2h, 3h, 4h, 5h, 6h, 7h, 8h, 9h,
10h, 11h, 12h, 13h, 14h, 15h
```

30m is the floor — the shortest Total any sitting can divide.

---

## I3b — Sitting Lengths Come From One Derived Filter

A sitting length is offered for a Total when **all four** hold:

```
1. Total % sitting == 0          exact division (I1)
2. sitting % 5 == 0              round numbers only
3. sitting >= 10 minutes         floor
4. sitting <= 120 minutes        ceiling
```

`sittingsFor(total)` = every length in 10…120 step 5 dividing the Total. **No
curated list, no per-Total exception, no fallback branch.** Every one of the
20 Totals yields at least 2 options as a *consequence*, not a rule (pinned by
`DurationsTest`). Does not apply `MIN_PIECES`.

---

## I4 — Focus Level Sets Defaults, Never Limits

Level determines pre-selected values only; it must never lock a user out of a
duration.

| Level | Totals offered | Default Total | Default Sitting |
|---|---|---|---|
| Beginner | all of I3's ladder | 2h | 30 min |
| Intermediate | all of I3's ladder | 6h | 60 min |
| Advanced | all of I3's ladder | 12h | 90 min |

Levels double as wheel/anchor fast-travel targets (`totalAnchors`,
`sittingAnchors` in Durations.kt). Duplicate anchors on small option sets are
**dropped**, not disabled.

Level is **user-editable** ([ProfileViewModel.saveLevel](app/src/main/java/com/artoffocus/app/ui/profile/ProfileViewModel.kt)),
including **mid-painting** — deliberate. Editing only moves where future
defaults land; it never touches the I2-frozen pair, the piece count, or
earned pieces. Level lives in [FocusSetupRepository](app/src/main/java/com/artoffocus/app/data/repository/FocusSetupRepository.kt),
**not** in [ProfileRepository](app/src/main/java/com/artoffocus/app/data/repository/ProfileRepository.kt)
(which owns only username/goal) — mirrors the web app's `focusSetupStorage.ts`
owning the level key.

---

## I5 — The 25-Minute Exception (resolved)

25 minutes doesn't divide evenly into whole-hour Totals, violating I1.
**Resolved:** dropped; the Beginner default sitting is 30 minutes. I1
outranks a suggested default.

---

## I6 — One Active Painting at a Time

Only one painting may be in progress; the current one must complete or cancel
before a new one is assigned.

Enforced in [ArtworkRepository.assignIfAbsent](app/src/main/java/com/artoffocus/app/data/repository/ArtworkRepository.kt) —
**not** in domain — which refuses to replace a live painting. Bounds the
active slot only; the Museum is unbounded/append-only (I12). The Focus setup
screen is blocked (`selectSetup` no-ops) while a painting is active.

---

## I7 — No Progress Is Ever Lost

If the app is closed, backgrounded, or the process is killed, all
session/puzzle/painting progress is preserved; reopening resumes exactly
where the user left off.

**Elapsed time is derived from wall-clock timestamps, never a ticking
counter** — `elapsedMs(state, now)` in [Session.kt](app/src/main/java/com/artoffocus/app/domain/focus/Session.kt)
always computes `accumulatedMs + (now - startedAt)` when `RUNNING`, never
accumulates via a `Timer`/`Handler.postDelayed` loop that increments a
counter. This was verified live: killing and relaunching the app (and even a
full emulator restart) mid-session correctly showed the true elapsed wall-
clock time, not whatever a ticking loop happened to reach before being
killed.

A gap longer than **15 minutes** (`AWAY_PROMPT_MS`) is surfaced on return via
[FocusViewModel.checkAwayGapOnResume](app/src/main/java/com/artoffocus/app/ui/focus/FocusViewModel.kt),
wired to `Lifecycle.Event.ON_STOP` / `ON_START` in `MainActivity`:

> "You were away 23m — keep this time or discard it?"

The user decides; the app never silently chooses.

Consequences:
- `lastSeenAt` is recorded on `ON_STOP` **only to measure the gap**, never to
  pause.
- **Notifications never fire during a sitting.** [RestEndReceiver](app/src/main/java/com/artoffocus/app/notifications/RestEndReceiver.kt)
  re-checks session state before posting; the daily/streak workers explicitly
  return early if a sitting is `RUNNING`.
- Local storage (Room + DataStore) is the source of truth. Supabase only
  mirrors it — sync happens on sign-in and after painting completion, never
  inside the timer's path. Both directions obey this: `pushLocalWork` and
  `pullRemoteWork` are called from sign-in and completion only. The app must
  work fully offline with no account.
- **Community art is fetched ahead of time, never on demand.**
  `CommunityArtRepository.peek` reads the local cache alone, so starting a
  sitting never waits on a request; `topUp` is the only network call and runs
  well away from a session.
- **Choosing a picture happens before a session, never during one.** Your
  Pictures is reachable only from the idle screen, and a picture is copied to
  private storage at import — so no file I/O and no picker sits in the timer's
  path.

**Correction to an earlier claim here.** This section used to say that
WorkManager/AlarmManager notifications on Android are "reliable regardless of
app or process state", so the web version's platform-support detector
(`domain/notifications.ts`'s `supportLevel`) had no Android equivalent. That was
wrong, and it is worth recording *why*, because the claim caused a real bug to
go unexamined for several releases.

Scheduling an alarm correctly is not sufficient on Android. The OS freezes and
then kills a backgrounded process that has no visible ongoing work — stock
Android patiently, MIUI/ColorOS/Funtouch/One UI within seconds — and when an
OEM task cleaner force-stops an app it **cancels that app's pending alarms**.
A perfectly scheduled exact alarm then simply never fires. Separately, nothing
was left running to notice a *sitting* had finished, so only rest end had any
background path at all.

The answer is a foreground service, which is the only mechanism Android offers
for declaring work ongoing:
[SessionService.kt](app/src/main/java/com/artoffocus/app/notifications/SessionService.kt)
holds an ongoing notification for the duration of a session and posts the
sitting-complete notification that previously could not exist.

**The containment rule that keeps this inside I7.** The service **reads session
state and never writes it.** It derives every number it shows from wall-clock
timestamps through the pure functions in `domain/focus`, and the ViewModel's
ticker remains the only thing that advances a session. Delete the service and
the timer is unchanged — it loses resilience, not correctness. Pinned by
`SessionServiceTest`, which runs the service over a sitting whose time is
already up and asserts the persisted session comes back identical.

The alarm is also re-armed on app launch
([NotificationScheduler.reconcileRestEnd]) as well as on boot, app update and
clock change, because a force-stop cancels alarms while broadcasting nothing.

---

## I8 — Both Orientations Are First-Class

Portrait and landscape are both first-class. (Compose UI currently reflows
via standard constraint/weight layouts; a distinct landscape two-section
layout for the Focus screen, matching the web app's dedicated landscape
treatment, is a follow-up — tracked in MODULE_MAP.)

---

## I9 — Quitting Has Friction, Starting Does Not

Cancelling requires a **five-second visible countdown** + confirmation.
Starting is one tap. Verified live: [FocusViewModel.requestCancel](app/src/main/java/com/artoffocus/app/ui/focus/FocusViewModel.kt)
drives a visible "Cancelling in 4…" countdown with a disabled "Confirm
cancel" button until it reaches zero, plus a "Keep going" escape hatch — never
a mute disabled control.

---

## I10 — Sitting Completion Defaults to Continuing

A piece reveals **immediately** on sitting completion — never gated behind a
decision. A visible **60-second countdown** (`AUTO_CONTINUE_MS`) then offers
Continue or Take Rest. If it expires with no choice, **the next sitting
begins** (`FocusViewModel.startAutoContinueCountdown`). The countdown is
**UI/ViewModel-scoped state only**, never persisted session state, and is
suppressed on the final sitting (completing the painting instead — see I12).

---

## I11 — Rest Completion Never Auto-Starts

Rest timer ending enters `REST_COMPLETE` and **stays there**
([Session.kt](app/src/main/java/com/artoffocus/app/domain/focus/Session.kt)'s
`completeRest`). Next sitting only begins on explicit user action
(`chooseContinue`). Rest has its own clock (`restStartedAt`, `restMinutes`);
`completeSitting` freezes `accumulatedMs` at the sitting length so the focus
clock can't be read as already-finished rest time. No code path may
transition `REST_COMPLETE → RUNNING` without going through `continueFromRest`
— pinned by `SessionTest`. `continueFromRest` from `RESTING` is a no-op
before the 60s continue-lock (`CONTINUE_LOCK_MS`) expires (D30 — extends I9,
does not apply it).

---

## I12 — A Completed Painting Is Never Destroyed

| Action | Active slot | Museum |
|---|---|---|
| Finish (all pieces uncovered) | released | **record written** |
| Abandon (mid-painting) | released | nothing written |

[FocusViewModel.finishPainting](app/src/main/java/com/artoffocus/app/ui/focus/FocusViewModel.kt)
writes the Museum record **before** clearing the active slot. Writes are
idempotent by `startedAt + seed` (`paintingKey`, enforced by Room's
`OnConflictStrategy.IGNORE` in [PaintingDao](app/src/main/java/com/artoffocus/app/data/local/PaintingDao.kt)).
[PaintingDao](app/src/main/java/com/artoffocus/app/data/local/PaintingDao.kt)
deliberately exposes **no update or delete method** — mirroring
`supabase/schema.sql`'s `paintings` table, which has no UPDATE/DELETE RLS
policy (both denied to everyone by omission). One malformed record must not
cost the whole collection: [MuseumRepository](app/src/main/java/com/artoffocus/app/data/repository/MuseumRepository.kt)
drops unconvertible rows individually on read.

**A photo-backed painting's picture is part of the painting.** When the art is
the user's own photo rather than a derivable gradient, the stored file carries
the same permanence as the record: it is copied into app-private storage at
import (never referenced by a revocable gallery URI), and nothing may delete a
picture that a completed painting still refers to.

That last rule is enforced, not merely asserted: `domain/focus/PhotoUsage.kt`
computes which pictures are in use, and
[PhotoLibraryViewModel](app/src/main/java/com/artoffocus/app/ui/photos/PhotoLibraryViewModel.kt)
refuses to delete one of them. The picture in the **active** painting is
covered too — losing it mid-session would destroy work in progress (I7).
`PhotoStore.delete` itself is a dumb file operation and enforces nothing, so
any new caller must make the same check. Locking concerns deletion only: a
locked picture stays selectable, because using a picture never consumes it.

**Only the user's own pictures are locked.** `picturesInUse` filters on
`ArtSource.OWN_PHOTO`. Community art also carries an `imageRef`, but it lives
in the disposable art cache, not the user's library — locking on it would make
the Pictures tab claim files it does not own. A cached community painting that
has been used is marked, never deleted, for the same I12 reason: a completed
painting still points at that image.

**Syncing only ever adds.** Pulling from the server was deliberately not built
for a long time, because two devices with divergent museums need a merge rule
and guessing at one risks this invariant. The rule, now that pulling exists
(`SyncRepository.pullRemoteWork`), is that a pull can only INSERT:

- it goes through `PaintingDao.insert`, which is `OnConflictStrategy.IGNORE`
  keyed on `paintingKey` ("startedAt:seed"), so a painting already held is
  skipped rather than rewritten — this relies on the unique index added in v5,
  whose absence meant the guarantee was previously unenforced;
- `PaintingDao` exposes no update and no delete at all, so "a pull overwrote my
  painting" is not a bug to be avoided here — it is unrepresentable;
- a painting held locally and absent from the server is left untouched.

The server enforces the same shape: `paintings` has SELECT and INSERT policies
only, so no client can update or delete a completed painting even if it tried.

**Schema changes stay additive.** The database is at version 5;
`data/local/Migrations.kt` adds the nullable `photoUri` column (v2), the
`source` / `credit` columns (v3), `remoteUrl` (v4), and the unique index on
`paintingKey` (v5), touching no existing data — v3's back-fill only states what was already true of each row (an image
could previously only be the user's own photo).
`fallbackToDestructiveMigration()` drops the table and must never be used — the
failure mode of a missing migration is a loud crash, which is correct, and far
preferable to a silent wipe.

Every migration is covered by an instrumented test
(`app/src/androidTest/.../MigrationTest.kt`) asserting that every painting
survives the full v1 -> v4 path. The tests were verified by deliberately
sabotaging a migration to drop the table and confirming they failed — a green
migration suite that cannot detect a wipe would be worse than none.

---

## I13 — Only Four Things Are Ever Public

```
handle · display name · focus hours · current streak
```

Everything else stays private (email, goal, painting titles/images,
session/rest times, piece counts, completion dates, longest streak). The list
is **closed** — a fifth field requires amending this invariant.

The Friends module reuses `supabase/schema.sql` **as-is**: RLS restricts
rows, not columns, so the boundary is the `public_leaderboard` /
`friend_leaderboard` views' SELECT lists (both `security_definer`, so
`profiles` itself stays strictly owner-only) and the `find_by_handle`
function's exact-match-only lookup (no searchable view — the user base cannot
be enumerated).

---

## I14 — A Friendship Is Consensual, and Private to Its Two Parties

Exists only when the addressee accepts. **Only the addressee may accept** —
enforced in `supabase/schema.sql`'s RLS via `auth.uid() <> requester_id` in
both `USING` and `WITH CHECK`. One relationship = one row, stored least-first.
A third party cannot see a friendship exists. Group membership is a second,
independent, revocable basis for sharing the same four I13 fields — leaving a
group ends sharing immediately. No group is discoverable without its invite
code.

---

## I15 — An Earned Achievement Is Never Taken Away

Achievements are **derived, never stored**
([Achievements.kt](app/src/main/java/com/artoffocus/app/domain/achievements/Achievements.kt)) —
retroactive, with no unlock event to miss or back-fill. **Streak achievements
read `longestStreak`, never `currentStreak`** (`valueFor`) — a lapsed 30-day
streak still happened; reading current streak would revoke it. If a stored
unlock cache is ever added, it must be a cache, never the source of truth.
Achievements count finished paintings only (the Profile screen states this
scope).

---

## Native-specific notes (not in the original web INVARIANTS.md)

- **Local-first persistence split:** Room backs the append-only Museum table
  (naturally suited to a growing list with no update/delete). DataStore
  Preferences backs the small singleton state pieces (session, active
  painting, setup, profile, onboarding, notification prefs, account/sync) —
  see [AppPreferences.kt](app/src/main/java/com/artoffocus/app/data/local/AppPreferences.kt).
  Every repository follows the same "validate on read, discard invalid state,
  never repair it" discipline as the web app's `state/*.ts` modules.
- **Notifications need a foreground service to be reliable**, not merely a
  correctly scheduled alarm: Android kills backgrounded processes and an OEM
  task cleaner cancels their pending alarms when it does. `SessionService`
  holds an ongoing notification for the length of a session; the service reads
  session state and never writes it, so the timer's correctness does not depend
  on it — see the correction under I7.
