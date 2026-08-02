# ADR 0034: Minimal reliable lobby ownership and snapshots

- Status: Accepted
- Date: 2026-08-02
- Decision owners: Sunderfront maintainers
- Related: #28, #76, #81, #89, #86

## Context

The production TLS listener authenticates a client with Identity Proof V2, applies
server identity policy, sends `SESSION_ADMISSION_RESULT`, and transfers an
`AuthorizedPlayerSession` into a bounded pre-lobby queue. Until this decision,
no process-owned component consumed that queue. A technically admitted client
therefore had no application-level confirmation that the server owned its
session and no bounded view of other connected players.

The first Direct Connect Alpha needs only a trustworthy vertical slice. It does
not need ready state, team selection, chat, map voting, realtime transport, world
synchronization, or reconnect. The lobby must remain outside the fixed-tick
simulation thread and must not weaken the existing capacity-before-policy
ordering.

## Decision

### Ownership transfer

`MinimalLobbyRuntime` consumes only `AuthorizedPlayerSession` instances from the
existing `AuthorizedPlayerSessionQueue`. It never accepts a player identifier,
handle, verification level, key, proof, or registry value supplied by a new
lobby payload.

A transferred session retains the global admission-capacity slot until its
idempotent `closeAsync()` completes. Polling the pre-lobby queue no longer makes
capacity available. This preserves the invariant that a full server rejects a
new connection before SQLite-backed first-use policy can create a local binding.

The queue owns pending sessions. The lobby owns transferred sessions. During
shutdown:

1. the TLS listener and admission gateway stop accepting new work and close
   pending queue entries;
2. the lobby closes every transferred member session;
3. registry refresh and the fixed-tick runtime close.

### Threading

The lobby has one owned virtual-thread coordinator plus owned virtual-thread
receive watchers. Queue polling, reliable send/receive, session close, snapshot
construction, and membership mutation never run on:

- the listener accept thread;
- an identity-admission worker;
- the fixed-tick simulation thread;
- a client renderer thread.

The coordinator is the only writer of membership and revision state. A bounded
command queue carries EOF, receive failure, and protocol-violation notifications
from watchers.

### Membership

Membership is keyed by the authenticated `PlayerId` and stores only:

- `PlayerId`;
- canonical handle;
- the server-owned authorized session.

A second active session with the same `PlayerId` is closed and rejected from the
lobby. The first session remains active. The lobby supports at most 40 members,
matching the project server target and protocol snapshot bound.

The revision is a positive monotonic 64-bit value. It increments for every
successful membership addition and removal. Overflow is a terminal runtime
failure rather than wraparound.

### Protocol

Two reliable message types are added without renumbering existing values:

- wire ID 10: `LOBBY_JOINED`;
- wire ID 11: `LOBBY_SNAPSHOT`.

Both use schema version 1 and big-endian integers.

`LOBBY_JOINED` contains:

1. one-byte schema version;
2. signed 64-bit positive lobby revision;
3. exact 56-byte canonical ASCII `PlayerId`;
4. one-byte handle length;
5. canonical ASCII handle bytes.

`LOBBY_SNAPSHOT` contains:

1. one-byte schema version;
2. signed 64-bit positive lobby revision;
3. one-byte member count from 0 through 40;
4. that many member records in strictly increasing `PlayerId` order.

A joining client receives `LOBBY_JOINED` first. After the membership is committed,
every current member receives a complete snapshot. Every later join or removal
also results in a complete snapshot. Delta messages are deliberately deferred:
a complete bounded view avoids gap recovery and ordering ambiguity in the first
alpha.

The codec rejects unsupported schema versions, non-positive revisions, invalid
sizes, non-ASCII or non-canonical domain values, duplicates, non-strict ordering,
truncation, trailing bytes, and snapshots above the payload/member bounds.

### Client messages

The minimal lobby defines no client-to-server lobby command. After the receive
watcher starts, any inbound envelope is a protocol violation and the server
closes that membership. EOF and receive failure also remove the member. Later
issues must introduce explicit message types before accepting ready state, chat,
teams, or gameplay input.

### Failure and supervision

All reliable sends share bounded timeouts. A member whose `LOBBY_JOINED` or
snapshot send fails is closed and removed. Snapshot delivery is repeated for the
remaining stable set after failed members are removed.

A terminal coordinator or shutdown failure is retained by the lobby and invokes
the process supervisor action. In `ServerLauncher`, that action stops the
fixed-tick runtime. The process must not continue with a dead lobby and a live
listener/simulation.

Diagnostics expose only a bounded event code, member count, and revision. They do
not expose addresses, key material, proof payloads, registry records, exception
text, or control over lifecycle.

### Validation boundary

The repository quality gate must compile the full server/process composition,
run protocol and ownership tests, and execute a real loopback path from
`ServerLauncher` through TLS, Identity Proof V2, policy admission,
`LOBBY_JOINED`, and the initial `LOBBY_SNAPSHOT`. The release workflow in #91
must repeat that path from unpacked distributions rather than from test-only
classpaths. A successful admission result alone is not sufficient evidence that
the first milestone can enter a lobby.

## Consequences

### Positive

- A client can prove it reached application-owned server state after admission.
- Full-server capacity remains enforced before identity-policy mutation.
- Membership has one writer and deterministic ownership/shutdown.
- Complete snapshots are simple for the first UI and Direct Connect service.
- The protocol does not trust client-supplied identity fields.
- Terminal lobby failure stops the server instead of creating a partial process.

### Negative

- Every membership change broadcasts the complete list; this is acceptable only
  because the list is capped at 40.
- Duplicate sessions do not yet replace or resume the old connection.
- Any client message is rejected until a later protocol issue introduces it.
- There is no heartbeat beyond transport failure/EOF detection.
- The revision is process-local and is not a reconnect or persistence token.

## Alternatives rejected

### Release capacity when polling the queue

Rejected because a full lobby would appear empty to admission. New clients could
reach policy evaluation and create local first-use bindings even though the
server had no membership capacity.

### Let the client send a join request with player identity

Rejected because authenticated identity already belongs to the server-side
session. Repeating it in a lobby request creates a spoofing and mismatch surface.

### Mutate lobby membership on the fixed-tick thread

Rejected because reliable network waits and close operations are not simulation
work and could stall deterministic ticks.

### Delta-only membership events

Rejected for the first alpha because missed or reordered deltas require recovery,
acknowledgement, and resynchronization rules. A full list of at most 40 entries is
bounded and deterministic.

### Keep the process alive after lobby failure

Rejected because the listener could continue admitting sessions into a queue
with no healthy consumer.

## Follow-up

- #88 consumes `LOBBY_JOINED` and `LOBBY_SNAPSHOT` in the production Direct
  Connect client service.
- #90 renders the immutable connected/lobby state without network work on the
  jMonkeyEngine render thread.
- #91 validates the exact launcher-to-client path from packaged release
  artifacts.
- Ready state, teams, chat, map selection, reconnect, and realtime gameplay need
  separate versioned protocol issues.
