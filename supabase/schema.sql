-- Art of Focus — Supabase schema.
--
-- Run once against a fresh project (SQL Editor, or `supabase db push`).
--
-- DESIGN NOTE — WHY THE LEADERBOARD IS A VIEW, NOT A TABLE QUERY
-- Row Level Security restricts *rows*, never *columns*: a policy that lets
-- someone read a profile row lets them read every column of it. And a plain
-- view runs as its creator, bypassing RLS entirely — so a view over `profiles`
-- would hand out exactly the columns its policies were meant to withhold.
-- `public_leaderboard` therefore selects only the three public columns AND is
-- declared security_invoker, so the caller's own RLS still applies.
-- See INVARIANTS I13.

-- ---------------------------------------------------------------- profiles

create table if not exists public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text not null default '',
  -- Mirrors of locally derived statistics. The client computes these; the
  -- server only stores them. Finished paintings only (D35).
  focus_minutes integer not null default 0 check (focus_minutes >= 0),
  current_streak integer not null default 0 check (current_streak >= 0),
  updated_at    timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Owner-only. A user may read and write their own row and no other.
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

create policy "profiles_insert_own" on public.profiles
  for insert with check (auth.uid() = id);

create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id) with check (auth.uid() = id);

-- ---------------------------------------------------------------- paintings

create table if not exists public.paintings (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users (id) on delete cascade,
  seed            bigint not null,
  title           text not null,
  total_minutes   integer not null check (total_minutes > 0),
  sitting_minutes integer not null check (sitting_minutes > 0),
  pieces          integer not null check (pieces >= 1),
  started_at      timestamptz not null,
  completed_at    timestamptz not null,

  -- Idempotency, matching paintingKey() in museum/domain/museum.ts exactly.
  -- Re-syncing the same museum must never duplicate a painting, and I6 allows
  -- only one active painting at a time, so no two can share a start instant.
  unique (user_id, started_at, seed)
);

alter table public.paintings enable row level security;

create policy "paintings_select_own" on public.paintings
  for select using (auth.uid() = user_id);

create policy "paintings_insert_own" on public.paintings
  for insert with check (auth.uid() = user_id);

-- No update or delete policy, deliberately: I12 says a completed painting is
-- never destroyed. Without a policy, both are denied for everyone.

create index if not exists paintings_user_completed_idx
  on public.paintings (user_id, completed_at desc);

-- ------------------------------------------------------- public projection

-- The ONLY thing other people can see (I13): three columns, nothing else.
-- No email, no goal, no painting titles, no session times.
--
-- security_definer, NOT security_invoker. This is the one place that choice is
-- correct, and the reasoning matters:
--
--   RLS cannot restrict columns. If we granted a broad SELECT on `profiles` so
--   an invoker-view could read other people's rows, that same grant would
--   expose EVERY column of those rows — the exact leak this view exists to
--   prevent. Instead `profiles` stays strictly owner-only, and this definer
--   view is the single narrow hole: it can see all rows, but it only ever
--   projects three columns.
--
-- Consequence: this view's SELECT list IS the privacy boundary. Adding a column
-- here publishes it to every user. Do not add one without amending I13.
create or replace view public.public_leaderboard
  with (security_barrier = true)
as
  select display_name, focus_minutes, current_streak
  from public.profiles;

-- Views run as their owner (postgres) by default, which is what lets this one
-- read across rows. Expose it read-only to signed-in users.
revoke all on public.public_leaderboard from anon, authenticated;
grant select on public.public_leaderboard to authenticated;

-- ================================================================
-- Stage 8b — friends (F11.1, F11.2)
-- ================================================================

-- Handles are the lookup key that makes friending possible (I13, amended).
-- citext: '@Ada' and '@ada' must not both exist, or two people could claim
-- visually identical handles.
create extension if not exists citext;

alter table public.profiles
  add column if not exists handle citext unique;

-- Policy-filtered column: exact-match search hits this on every lookup.
create index if not exists profiles_handle_idx on public.profiles (handle);

-- ---------------------------------------------------------------- friendships

-- ONE row per relationship, never two. The pair is stored least-first, so
-- "A befriends B" and "B befriends A" cannot both exist — two rows would
-- eventually disagree about the same relationship.
--
-- `requester_id` records who asked, independently of the ordering, because the
-- acceptance rule depends on it.
create table if not exists public.friendships (
  user_a       uuid not null references auth.users (id) on delete cascade,
  user_b       uuid not null references auth.users (id) on delete cascade,
  requester_id uuid not null references auth.users (id) on delete cascade,
  status       text not null default 'pending'
               check (status in ('pending', 'accepted')),
  created_at   timestamptz not null default now(),

  primary key (user_a, user_b),
  check (user_a < user_b),
  check (requester_id = user_a or requester_id = user_b)
);

alter table public.friendships enable row level security;

-- Indexes on every column the policies below filter on. Without these, RLS
-- subqueries degrade badly as the table grows.
create index if not exists friendships_user_a_idx on public.friendships (user_a);
create index if not exists friendships_user_b_idx on public.friendships (user_b);

-- Visible only to the two parties. A third user cannot see that a friendship
-- exists at all.
--
-- `(select auth.uid())` rather than bare `auth.uid()`: wrapping it lets Postgres
-- cache the value per statement instead of re-evaluating per row.
create policy "friendships_select_own" on public.friendships
  for select using (
    (select auth.uid()) = user_a or (select auth.uid()) = user_b
  );

-- You may only create a request you are actually making.
create policy "friendships_insert_own" on public.friendships
  for insert with check (
    (select auth.uid()) = requester_id
    and ((select auth.uid()) = user_a or (select auth.uid()) = user_b)
  );

-- THE ACCEPTANCE RULE. Only the addressee — the party who did NOT send the
-- request — may change the row. Without this the requester could flip their own
-- request to 'accepted' and acceptance would be decorative.
create policy "friendships_update_addressee_only" on public.friendships
  for update using (
    (select auth.uid()) <> requester_id
    and ((select auth.uid()) = user_a or (select auth.uid()) = user_b)
  ) with check (
    (select auth.uid()) <> requester_id
    and ((select auth.uid()) = user_a or (select auth.uid()) = user_b)
  );

-- Either party may withdraw or decline by deleting the row.
create policy "friendships_delete_own" on public.friendships
  for delete using (
    (select auth.uid()) = user_a or (select auth.uid()) = user_b
  );

-- ------------------------------------------------------- friend leaderboard

-- Accepted friends of the caller, plus the caller.
--
-- security_definer for the same reason as public_leaderboard (I13): profiles
-- stays strictly owner-only, and this view is the single narrow projection.
-- Its SELECT list is the privacy boundary — four public fields, nothing else.
create or replace view public.friend_leaderboard
  with (security_barrier = true)
as
  select p.handle, p.display_name, p.focus_minutes, p.current_streak
  from public.profiles p
  where
    p.id = (select auth.uid())
    or p.id in (
      select case when f.user_a = (select auth.uid()) then f.user_b else f.user_a end
      from public.friendships f
      where f.status = 'accepted'
        and ((select auth.uid()) = f.user_a or (select auth.uid()) = f.user_b)
    );

revoke all on public.friend_leaderboard from anon, authenticated;
grant select on public.friend_leaderboard to authenticated;

-- ------------------------------------------------------------ handle lookup

-- Exact-match handle search. A function rather than a view so nothing is
-- enumerable: you must already know the handle. Returns at most one row, and
-- only the public fields.
create or replace function public.find_by_handle(wanted citext)
returns table (id uuid, handle citext, display_name text)
language sql
security definer
set search_path = public
as $$
  select p.id, p.handle, p.display_name
  from public.profiles p
  where p.handle = wanted and p.id <> (select auth.uid())
  limit 1;
$$;

grant execute on function public.find_by_handle(citext) to authenticated;

-- ------------------------------------------------------------ claim a handle

-- Atomic claim. Checking uniqueness in the client would race: two users could
-- pass the check simultaneously and one write would fail with a raw constraint
-- error. This returns a plain, translatable status string instead.
create or replace function public.claim_handle(wanted citext)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := (select auth.uid());
begin
  if me is null then
    return 'not_signed_in';
  end if;
  if wanted is null or length(wanted) < 3 or length(wanted) > 20 then
    return 'invalid';
  end if;
  if wanted !~ '^[a-z0-9_]+$' then
    return 'invalid';
  end if;

  -- Taken by somebody else?
  if exists (select 1 from profiles where handle = wanted and id <> me) then
    return 'taken';
  end if;

  update profiles set handle = wanted, updated_at = now() where id = me;
  return 'ok';
exception
  -- Belt and braces: a concurrent claim can still lose the race here.
  when unique_violation then
    return 'taken';
end;
$$;

grant execute on function public.claim_handle(citext) to authenticated;

-- ---------------------------------------------------------- pending requests

-- Pending requests need the OTHER party's handle and name — but they are not
-- friends yet, so `friend_leaderboard` (accepted only) cannot resolve them, and
-- `profiles` is owner-only. This function is the narrow exception: it returns
-- the public fields for people who have an actual pending relationship with the
-- caller, and nobody else.
create or replace function public.pending_requests()
returns table (
  other_id     uuid,
  handle       citext,
  display_name text,
  outgoing     boolean
)
language sql
security definer
set search_path = public
as $$
  select
    p.id,
    p.handle,
    p.display_name,
    (f.requester_id = (select auth.uid())) as outgoing
  from public.friendships f
  join public.profiles p
    on p.id = case
                when f.user_a = (select auth.uid()) then f.user_b
                else f.user_a
              end
  where f.status = 'pending'
    and ((select auth.uid()) = f.user_a or (select auth.uid()) = f.user_b);
$$;

grant execute on function public.pending_requests() to authenticated;

-- ================================================================
-- F11.3 — groups
-- ================================================================
--
-- Groups let people compare focus WITHOUT being friends (I14, amended).
-- Joining is its own act of consent: members see the four public I13 fields —
-- handle, display name, focus hours, current streak — and nothing else.
-- Leaving ends it.
--
-- NO DIRECTORY. There is no group search and no public list, for the same
-- reason handle search is exact-match only (D40): the app must not be
-- enumerable. An invite code is a group's handle — unguessable, and you have
-- to be given it.

create table if not exists public.groups (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (length(trim(name)) between 1 and 40),
  -- citext so a code read aloud and retyped in any case still works.
  invite_code citext not null unique,
  owner_id    uuid not null references auth.users (id) on delete cascade,
  created_at  timestamptz not null default now()
);

create table if not exists public.group_members (
  group_id  uuid not null references public.groups (id) on delete cascade,
  user_id   uuid not null references auth.users (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

alter table public.groups enable row level security;
alter table public.group_members enable row level security;

-- Indexes on every column the policies below filter on.
create index if not exists group_members_user_idx on public.group_members (user_id);
create index if not exists group_members_group_idx on public.group_members (group_id);
create index if not exists groups_invite_code_idx on public.groups (invite_code);

-- ---------------------------------------------------------------- recursion
--
-- THE TRAP THIS AVOIDS.
-- The natural policies are mutually recursive: "you may read a group if you
-- are a member of it" has to consult `group_members`, whose own policy says
-- "you may read a membership if you are in that group" — which consults
-- `groups`. Postgres detects the loop and errors.
--
-- A security definer function breaks the cycle: it runs as its owner, so RLS
-- does not re-enter while it executes. Both policies call this instead of
-- querying each other. Same pattern as find_by_handle and pending_requests.
create or replace function public.is_group_member(gid uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1 from public.group_members m
    where m.group_id = gid and m.user_id = (select auth.uid())
  );
$$;

grant execute on function public.is_group_member(uuid) to authenticated;

-- A group is visible only to its members. A non-member cannot see that it
-- exists at all — only `join_group` can reach it, and only by exact code.
create policy "groups_select_members" on public.groups
  for select using (public.is_group_member(id));

-- Memberships are visible only within the group they belong to.
create policy "group_members_select_same_group" on public.group_members
  for select using (public.is_group_member(group_id));

-- You may only ever remove yourself. Owner-initiated removal is a policy
-- question of its own and is deliberately not built.
create policy "group_members_delete_self" on public.group_members
  for delete using ((select auth.uid()) = user_id);

-- -------------------------------------------------------------- standings
--
-- The ONLY thing group members learn about each other: the four public I13
-- fields. No goal, no painting titles, no session times, no email.
--
-- security definer for the same reason as friend_leaderboard — `profiles`
-- stays strictly owner-only and this is the single narrow projection. THE
-- SELECT LIST IS THE PRIVACY BOUNDARY. Adding a column here publishes it to
-- every member of every group; do not add one without amending I13.
create or replace function public.group_standings(gid uuid)
returns table (
  handle         citext,
  display_name   text,
  focus_minutes  integer,
  current_streak integer
)
language sql
security definer
stable
set search_path = public
as $$
  select p.handle, p.display_name, p.focus_minutes, p.current_streak
  from public.group_members m
  join public.profiles p on p.id = m.user_id
  -- The caller must be a member. Without this guard a definer function would
  -- happily hand any group's standings to anyone who knew its id.
  where m.group_id = gid and public.is_group_member(gid);
$$;

grant execute on function public.group_standings(uuid) to authenticated;

-- ------------------------------------------------------------ my groups
--
-- Definer, because `groups_select_members` would otherwise need the caller to
-- already know the id. Returns only groups the caller actually belongs to.
create or replace function public.my_groups()
returns table (
  id          uuid,
  name        text,
  invite_code citext,
  owner_id    uuid,
  member_count bigint
)
language sql
security definer
stable
set search_path = public
as $$
  select
    g.id, g.name, g.invite_code, g.owner_id,
    (select count(*) from public.group_members m2 where m2.group_id = g.id)
  from public.groups g
  join public.group_members m on m.group_id = g.id
  where m.user_id = (select auth.uid())
  order by g.created_at;
$$;

grant execute on function public.my_groups() to authenticated;

-- ------------------------------------------------------------ create / join
--
-- RPCs rather than client inserts. A client-side uniqueness check on the
-- invite code would race, and a lost race surfaces a raw Postgres constraint
-- error to the user — exactly what claim_handle exists to prevent (D40).

create or replace function public.create_group(group_name text, code citext)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  me  uuid := (select auth.uid());
  gid uuid;
begin
  if me is null then return 'not_signed_in'; end if;
  if group_name is null or length(trim(group_name)) = 0 then return 'invalid_name'; end if;
  if length(trim(group_name)) > 40 then return 'invalid_name'; end if;
  if code is null or length(code) <> 8 then return 'invalid_code'; end if;

  if exists (select 1 from groups where invite_code = code) then
    return 'code_taken';
  end if;

  insert into groups (name, invite_code, owner_id)
  values (trim(group_name), code, me)
  returning id into gid;

  -- The creator is a member; a group with an owner outside it makes no sense.
  insert into group_members (group_id, user_id) values (gid, me);
  return 'ok';
exception
  when unique_violation then
    return 'code_taken';
end;
$$;

grant execute on function public.create_group(text, citext) to authenticated;

create or replace function public.join_group(code citext)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  me  uuid := (select auth.uid());
  gid uuid;
begin
  if me is null then return 'not_signed_in'; end if;

  select id into gid from groups where invite_code = code;
  -- Deliberately the same answer for a malformed code and a real one that does
  -- not exist: distinguishing them would let someone probe for valid codes.
  if gid is null then return 'not_found'; end if;

  if exists (select 1 from group_members where group_id = gid and user_id = me) then
    return 'already_member';
  end if;

  insert into group_members (group_id, user_id) values (gid, me);
  return 'ok';
exception
  when unique_violation then
    return 'already_member';
end;
$$;

grant execute on function public.join_group(citext) to authenticated;
