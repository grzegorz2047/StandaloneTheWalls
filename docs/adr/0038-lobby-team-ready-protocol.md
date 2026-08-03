# ADR 0038: Reliable lobby team and readiness protocol

- Status: Accepted
- Date: 2026-08-03
- Decision owners: Sunderfront maintainers
- Related: #2, #14, #21, #88, #89, #120, #122

## Context

ADR 0037 places team selection, balancing, readiness and the start predicate in a
renderer- and transport-independent domain. The existing Direct Connect minimal
lobby authenticates a session, admits it through identity policy, sends a bounded
membership snapshot and rejects every later client message.

The next slice must expose team and ready intent without allowing the protocol,
network worker or client to become a second source of gameplay truth. It must
also preserve bounded payloads and fail-closed session ownership while remaining
compatible with membership-only snapshots produced by the previous alpha.

## Decision

### Wire catalog

Reliable message IDs 10 and 11 retain their existing meanings:

- `LOBBY_JOINED` identifies the authenticated session after admission;
- `LOBBY_SNAPSHOT` is the complete canonical roster at one revision.

IDs 12 through 14 are reserved for:

- `LOBBY_SELECT_TEAM`;
- `LOBBY_SET_READY`;
- `LOBBY_COMMAND_RESULT`.

Every new payload is schema-versioned, big-endian, exact-length and below its
message-specific protocol bound. Unknown schemas, request IDs below one, unknown
team or outcome codes, non-canonical booleans, truncation and trailing bytes are
rejected before domain code runs.

### Snapshot compatibility

The server encodes `LOBBY_SNAPSHOT` schema v2. Each member contains the existing
public `playerId` and canonical handle followed by one explicit team code and one
canonical ready byte.

The decoder accepts schema v1 from older servers and maps every member to
`UNASSIGNED` and `ready=false`. It does not infer a team or preserve an unknown
value. A schema-v2 member cannot be ready while unassigned.

The protocol module defines its own stable `LobbyTeam` wire enum because protocol
cannot depend on `game-domain`. The server adapter performs an exhaustive mapping
to and from the domain `TeamId` enum.

### Trusted identity boundary

Client command payloads contain only a positive request ID and the requested team
or ready value. They do not contain player ID, handle, session UUID, team counts,
revision claims or another participant selector.

`MinimalLobbyRuntime` derives `LobbyParticipantId` from the already authenticated
server session. A receive worker may decode a bounded envelope and enqueue the
result, but it cannot choose the authoritative participant or mutate the roster.

### Coordinator ownership

One process-owned lobby coordinator is the sole writer of `LobbyRosterState`.
Join, leave, team selection and readiness all execute through
`LobbyRosterRules`. The runtime does not repeat balancing or readiness rules.

Each session owns a strictly increasing positive request-ID sequence. A repeated
or lower request ID is a protocol violation and closes only that session. Request
IDs are correlation and replay-detection values, not persistent reconnect tokens.

For a valid command the coordinator sends one `LOBBY_COMMAND_RESULT` to its
author. The result contains the request ID, the current authoritative roster
revision and either `APPLIED`, `NO_CHANGE` or one stable domain-rejection code.
After an actual state change, and only after the result has been sent, the
coordinator broadcasts one full schema-v2 snapshot. Rejected and idempotent
commands do not advance revision or create a false broadcast.

### Failure and threading

Reliable receive and send operations remain outside the fixed-tick simulation
thread. Malformed commands, unexpected message types, replayed request IDs, EOF,
receive failure and send failure deterministically remove the owned participant
through the domain `Leave` command, close the session and release capacity once.
A snapshot send failure may remove additional failed sessions and recompute the
full roster until the remaining members share one stable revision.

This protocol does not start a countdown or round. A later adapter will consume
the domain `readyToStart` transition and submit a bounded command to the existing
match lifecycle.

## Consequences

### Positive

- The client cannot select a team or ready state for another identity.
- Team balancing and readiness retain one authoritative domain implementation.
- Every result is correlated to one request and one concrete roster revision.
- Legacy schema-v1 snapshots remain readable without guessing missing state.
- Full snapshots give all clients the same canonical recovery point after every
  visible change.
- Network work and lobby coordination remain outside fixed-tick simulation.

### Negative

- Schema-v1 clients cannot understand schema-v2 snapshots emitted by the new
  server and must fail explicitly rather than silently ignore team state.
- Full snapshots are less bandwidth-efficient than deltas, although the roster is
  capped at 40 members and remains below 4 KiB.
- Strict per-session request ordering means a client must serialize these lobby
  intents until a reconnect protocol exists.
- Sending the command result before the snapshot creates two ordered messages for
  a successful mutation; consumers must treat the snapshot as roster truth.

## Alternatives rejected

### Put player identity in every command

Rejected because the authenticated session already establishes identity. A
client-supplied participant field would add an avoidable spoofing surface.

### Let receive workers update the roster

Rejected because concurrent workers would create multiple writers, non-
deterministic revisions and race-dependent snapshots.

### Send only deltas

Rejected for this first bounded slice because loss, reconnect and partial failure
would require a second recovery protocol. A full roster under 4 KiB is simpler to
validate and reproduce.

### Increase revision for rejection or retry

Rejected because a request is not a visible roster change. The result echoes the
unchanged current revision instead.

### Start `MatchLifecycle` when the roster becomes ready

Rejected because countdown ownership, cancellation and round-epoch reset are a
separate server-to-simulation integration boundary.

## Follow-up

- Add keyboard-first client controls and localized result messages without moving
  authority into jMonkeyEngine UI.
- Connect the authoritative `readyToStart` edge to `MatchLifecycle` through a
  bounded command outside the lobby network worker.
- Define lobby/round epoch and reconnect semantics before reusing request IDs
  across resumed sessions.
