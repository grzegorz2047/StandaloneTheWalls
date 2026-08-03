# ADR 0039: Authoritative lobby countdown driven by simulation ticks

- Status: Accepted
- Date: 2026-08-03
- Issues: #128, #21, #120, #122, #127

## Context

The authoritative lobby roster already derives `readyToStart` from one canonical
set of rules: the configured minimum is present, every participant has a team and
is ready, and at least two teams are represented. The existing match lifecycle,
however, previously started its countdown from connected-player count alone. That
would allow a match to start with unassigned or unready players and would duplicate
lobby policy outside the roster domain.

The server also has a fixed-tick simulation source and a separate reliable lobby
coordinator. Network I/O must remain outside the simulation thread, while countdown
time must be based on authoritative ticks rather than a wall clock, UI timer, or a
second scheduler. Joining, leaving, team changes, readiness changes, send failures,
and shutdown must all influence the same state in a deterministic order.

Clients need a bounded full snapshot for joining during a countdown and must not
silently accept stale, duplicated, skipped, or roster-inconsistent phase state.

## Decision

### Domain lifecycle input

`MatchLifecycle` accepts `UpdateLobbyState(connectedPlayers, readyToStart)` instead
of a count-only lobby update.

- Connected-player count alone never starts a countdown.
- `readyToStart=true` below the configured minimum is rejected as an impossible
  authoritative input.
- While waiting, a valid ready lobby enters `START_COUNTDOWN` with the full
  configured duration.
- Losing readiness before preparation returns to `WAITING_FOR_PLAYERS` with one of
  two bounded reasons: `INSUFFICIENT_PLAYERS` or `LOBBY_NOT_READY`.
- Re-establishing readiness starts a new full countdown.
- A readiness change processed before the final countdown tick cancels the start.
- After entering `PREPARATION`, roster count updates do not roll the phase back.

### Single match coordinator

`MinimalLobbyRuntime` owns one `LobbyMatchCoordinator` beside its authoritative
`LobbyRosterState`. Only the existing lobby coordinator thread mutates either
state.

The match coordinator:

- requires identical minimum-player policy in lobby and match configuration;
- accepts only the next roster revision, treating an identical repeated revision
  as idempotent and rejecting backward, gapped, or conflicting revisions;
- accepts only sequential simulation tick numbers, treating the same tick as
  idempotent and rejecting backward or skipped ticks;
- increments an independent visible match revision only when roster-derived match
  state, countdown time, phase, or cancellation reason changes;
- freezes lifecycle progression after the single transition to `PREPARATION`,
  because map loading, spawning, and later match phases are outside this slice.

### Fixed-tick mailbox

The simulation thread calls `MinimalLobbyRuntime.offerSimulationTick(tickNumber)`.
The operation is non-blocking and uses constant memory:

- an atomic latest sequential tick;
- at most one queued signal for the lobby coordinator.

The simulation thread performs no network I/O, roster mutation, match transition,
session close, queue polling, sleep, or wall-clock countdown calculation. The
lobby coordinator drains every sequential offered tick and broadcasts changes.
Failure to signal the bounded coordinator queue is a fatal runtime error rather
than an implicitly lost tick.

### Reliable wire snapshot

Reliable message ID 15 is `LOBBY_MATCH_SNAPSHOT`. Schema v1 is an exact 44-byte,
big-endian payload:

```text
schemaVersion:u8
matchRevision:i64
rosterRevision:i64
authoritativeTick:i64
phase:u8
ticksRemaining:i64
connectedPlayers:u8
roundNumber:i64
cancellationReason:u8
```

`authoritativeTick=-1` means that no simulation tick has been processed yet.
Protocol enums use explicit wire codes independent of `game-domain` enum ordinals:

- phases: waiting, start countdown, preparation;
- cancellation reasons: none, insufficient players, lobby not ready.

The decoder rejects invalid size, schema, negative revisions or countdown values,
unknown codes, unsupported player counts, invalid rounds, and internally
inconsistent phase/reason combinations before exposing the value object.

### Snapshot ordering and failures

After a real roster change the server sends:

1. the full canonical `LOBBY_SNAPSHOT`;
2. the full `LOBBY_MATCH_SNAPSHOT` describing exactly that roster revision and
   participant count.

Countdown-only changes send only a new match snapshot. A new participant therefore
receives the full roster and full current phase state. Joining during countdown
adds an unassigned, not-ready participant, so the same authoritative roster rules
cancel the countdown and all sessions receive one complete waiting snapshot with a
bounded reason.

A send failure removes the affected participant through the normal domain-owned
leave path and re-stabilizes both snapshots. No separate membership or countdown
state is repaired by transport code.

### Client revision policy

Direct Connect admission now completes only after this ordered sequence:

```text
LOBBY_JOINED -> LOBBY_SNAPSHOT -> LOBBY_MATCH_SNAPSHOT
```

`ConnectedLobbySession` owns immutable roster and match snapshots. For subsequent
match snapshots it requires exactly the next match revision. It closes fail-closed
on:

- a backward revision;
- a duplicate revision;
- a revision gap;
- a roster revision mismatch;
- a connected-player count mismatch;
- a malformed payload or unexpected message.

The UI publishes roster and phase state together only when both snapshots describe
the same roster revision and participant count. It never decrements countdown time
locally.

### Preparation lock

After `PREPARATION`:

- server team and ready commands return bounded outcome
  `MATCH_ALREADY_STARTED` without changing the roster or closing a valid session;
- client team and ready controls remain visible but disabled;
- keyboard and pointer focus exclude those controls;
- the authoritative preparation status is displayed.

`PREPARATION` is terminal for this implementation slice. Advancing into walls,
combat, results, and reset requires the future world/map runtime.

### Lifecycle and shutdown

Production composition starts the lobby before the TLS listener and simulation.
Shutdown stops new TLS admission, stops the simulation thread, closes lobby-owned
sessions and workers, and finally closes the registry refresh scheduler. No
countdown timer, scheduled task, or simulation callback survives process shutdown.

## Consequences

- Readiness has real server-authoritative gameplay meaning instead of being a UI
  label.
- Two clients observe the same phase, tick, countdown value, cancellation reason,
  and transition to preparation.
- Countdown progress is reproducible from sequential inputs and independent of
  rendering frame rate or wall-clock jitter.
- A full match snapshot is sent after every countdown tick. The payload is small
  and bounded; delta compression is deliberately deferred until evidence shows it
  is necessary.
- Strict revision gaps are fail-closed. Future reconnect/resume support must add an
  explicit resynchronization protocol rather than relaxing this invariant.
- Joining during countdown currently cancels it because every participant is
  required to choose a team and become ready. Spectator joins or deferred round
  admission require a separate policy.
- The server reaches `PREPARATION` exactly once but does not yet load a map, spawn
  players, or start the preparation timer beyond this frozen boundary.

## Rejected alternatives

- A `ScheduledExecutorService`, wall-clock timer, or UI countdown: creates another
  clock and races fixed ticks.
- Starting from player count only: ignores authoritative team and ready rules.
- Mutating match state on the simulation thread and sending from there: introduces
  network I/O and session ownership into the fixed-tick boundary.
- Best-effort acceptance of duplicate or skipped match revisions: hides state loss
  and can display a countdown inconsistent with the roster.
- Optimistic client phase changes: makes presentation a second source of truth.
