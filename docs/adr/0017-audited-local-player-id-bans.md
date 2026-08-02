# ADR 0017: Audited local player-ID bans

- Status: Accepted
- Date: 2026-08-02
- Issues: #64, #31
- Depends on: #62, #63

## Context

A canonical-handle binding protects a name but does not express whether the
stable cryptographic player identity may join the server. Banning a handle can be
bypassed by choosing another local handle. Removing a binding would also destroy
TOFU history and could release the old name to another key.

The server therefore needs a separate local ban keyed only by public `playerId`,
with the same atomic audit guarantees as handle administration and persistence
across restart.

## Decision

Add a renderer-independent player-ban policy above the authenticated `playerId`.
`PlayerBanAdmissionService` returns only `ALLOWED` or `PLAYER_BANNED` and accepts
no handle input. Runtime integration must evaluate this decision before
canonical-handle authorization.

A current ban stores the public player ID, explicit ban instant, bounded
administrator ID and bounded NFC reason. Atomic administration supports:

- ban an unbanned player;
- idempotently report an already banned player without another event;
- unban a currently banned player;
- report an unbanned player without another event.

Every applied ban or unban and exactly one append-only audit event must commit in
one store operation. Audit or ban-capacity exhaustion blocks the mutation. Ban
records and events never contain handles, private keys, IP addresses, registry
snapshots or network-session state.

Ban and unban do not modify local canonical-handle bindings. Unbanning a player
therefore restores admission with the same historical TOFU ownership instead of
creating a new first-use race.

`identity-policy-sqlite` migrates schema v1 to v2 in one `BEGIN IMMEDIATE`
transaction. The migration creates current-ban and append-only ban-audit tables,
installs update/delete protection triggers and updates schema metadata only after
all objects exist. Existing handle bindings and handle-audit values remain
unchanged.

The handle store accepts both schema v1 and schema v2. New handle-only databases
start at v1; opening the player-ban adapter performs the one-way migration to v2.
Both adapters reject versions newer than v2.

## Consequences

- A player cannot bypass a ban by changing the canonical handle.
- Banning does not release or rewrite any existing handle binding.
- Concurrent ban attempts produce one state and one audit event.
- Failed audit insertion rolls back the current-ban mutation.
- Existing local identity databases migrate without discarding handle history.
- Timed bans, IP/device bans, central ban synchronization, command permissions,
  active-session disconnection and TLS/lobby wiring remain separate work.
