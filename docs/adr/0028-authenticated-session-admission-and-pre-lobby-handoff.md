# ADR 0028: Authenticated session admission and bounded pre-lobby handoff

- Status: Accepted
- Date: 2026-08-02
- Issues: #31, #34, #76

## Context

The reliable transport already provides TLS 1.3 server authentication, one agreed
session UUID, RFC 9266 channel binding, and an Identity Proof V2 exchange. A
successful exchange returns an open `AuthenticatedReliableSession` containing the
verified `playerId` and canonical handle. Separately, `LocalIdentityRuntime` owns
the durable player bans, local handle bindings, signed registry state, and the
fail-closed `LOCAL_TOFU`, `GLOBAL_ONLY`, and `HYBRID` policies.

A cryptographically valid proof is necessary but not sufficient for entering a
lobby. The server must still reject a banned player, a conflicting local binding,
a missing or stale registry in a strict mode, a revoked global handle, or a global
player mismatch. That policy evaluation may use SQLite and must not run on the
fixed-tick simulation thread. A successful session also needs a bounded ownership
handoff so a slow or absent lobby consumer cannot create unbounded live channels.

## Decision

### Ordered admission pipeline

One `TlsIdentityAdmissionGateway` owns the server-side pipeline for every accepted
TLS connection:

1. complete the existing TLS session bootstrap;
2. complete the existing Identity Proof V2 exchange;
3. stop immediately if gateway shutdown has begun;
4. reserve one slot in the bounded pre-lobby queue;
5. call the one process-owned `LocalIdentityRuntime.admit(handle, playerId)`;
6. map the semantic result to a stable post-authentication wire status;
7. send exactly one `SESSION_ADMISSION_RESULT` message;
8. commit an accepted `AuthorizedPlayerSession` into the reserved slot, or cancel
   the reservation for every rejection;
9. transfer ownership to the future lobby only when the lobby polls or drains the
   queue.

No SQLite, registry, socket, or blocking wait runs on the listener accept thread or
the simulation thread. The gateway uses owned named virtual threads and has one
bounded close timeout.

### Wire boundary

`SESSION_ADMISSION_RESULT` is distinct from the existing `IDENTITY_RESULT`.
`IDENTITY_RESULT` proves only that the challenge response is cryptographically
valid for the transport-derived server ID, session ID, and channel binding.
`SESSION_ADMISSION_RESULT` reports the later server policy decision.

The admission payload v1 is exactly two bytes:

1. schema version `1`;
2. one unsigned stable status code.

Accepted statuses are:

- `GLOBAL_ACCEPTED`;
- `LOCAL_FIRST_USE_ACCEPTED`;
- `LOCAL_RETURNING_ACCEPTED`.

Policy rejections preserve the existing bounded meanings:

- `PLAYER_BANNED`;
- `REGISTRY_UNAVAILABLE`;
- `REGISTRY_STALE`;
- `UNKNOWN_GLOBAL_HANDLE`;
- `REVOKED_GLOBAL_HANDLE`;
- `GLOBAL_PLAYER_MISMATCH`;
- `LOCAL_BINDING_CONFLICT`;
- `LOCAL_BINDING_CAPACITY_EXCEEDED`.

Operational rejections add:

- `SERVER_CAPACITY_EXCEEDED` when no pre-lobby slot can be reserved;
- `SERVER_SHUTTING_DOWN` when lifecycle ownership is closing.

The payload contains no exception text, SQLite state, IP address, key bytes,
signature, registry artifact, or audit reason.

### Authorized session

An `AuthorizedPlayerSession` is immutable and contains only:

- the transport-owned session UUID;
- the authenticated server ID;
- the verified player ID;
- the canonical handle;
- `LOCAL_UNVERIFIED` or `GLOBAL_VERIFIED`;
- the still-open post-identity reliable channel.

It does not contain private keys, raw proof payloads, nonce bytes, channel-binding
bytes, IP addresses, or mutable registry state.

### Bounded handoff and ownership

The queue has a configured positive capacity no greater than 10,000. Admission
reserves a slot after cryptographic proof but before the SQLite-backed identity
policy. A reservation is capacity ownership, not lobby admission, and is cancelled
for every policy rejection. This order prevents a capacity-rejected `LOCAL_TOFU`
attempt from creating a binding. A reservation counts against capacity and can be
committed or cancelled exactly once. Therefore two concurrent attempts cannot both
receive acceptance for one remaining slot.

The queue owns committed sessions until `poll` or `drain` transfers ownership to
the caller. Closing the queue closes every session that has not been transferred.
A full or closed queue never blocks the transport worker.

### Failure and shutdown

Every rejected or failed path closes the authenticated session. Closing the
underlying channel closes the accepted TLS lease and releases listener admission
idempotently. The gateway tracks in-flight accepted connections so shutdown can
close bootstrap, proof, policy, and result-send work in progress.

Shutdown order is:

1. change gateway state from open to closing;
2. reject new accepted connections;
3. interrupt owned workers and close tracked TLS leases;
4. wait for the workers for a bounded duration so every outstanding queue
   reservation is committed or cancelled;
5. close every queued, untransferred authorized session;
6. mark the gateway closed.

Waiting for workers before closing the queue prevents a completed accepted result
from losing its reserved handoff slot to a concurrent queue close. If shutdown
closes the TLS lease before the result send completes, the send fails and the
reservation is cancelled instead.

The gateway checks lifecycle after bootstrap and again after cryptographic proof,
before reserving capacity or invoking SQLite-backed policy. It never begins a new
admission operation after shutdown ownership has started. An operation that already
reserved capacity is treated as in flight and is resolved during bounded shutdown.

Diagnostic observers receive only a bounded event code and, for a completed policy
or capacity decision, the stable admission status. Observer failures cannot affect
security or lifecycle.

## Consequences

- A copied handle without the player private key cannot reach policy evaluation.
- A valid player proof does not bypass player bans or handle policy.
- A banned first-use attempt cannot create a local binding because the shared
  runtime keeps ban evaluation before handle authorization.
- A capacity-rejected first-use attempt cannot create a local binding because
  capacity is reserved before the SQLite-backed policy runs.
- Queue pressure is explicit and bounded instead of accumulating open sessions.
- The future lobby consumes an already authorized session without reopening
  SQLite or duplicating registry state.
- Client support must distinguish cryptographic identity success from server
  admission success.
- Production credential loading, listener process configuration, public PKI,
  reconnect, realtime transport, and actual lobby membership remain separate
  work. This ADR does not introduce an insecure default listener or generate
  server credentials.

## Alternatives rejected

### Treat `IDENTITY_RESULT=ACCEPTED` as lobby authorization

Rejected because it would mix proof verification with mutable server policy and
would allow a cryptographically valid but banned or conflicting identity to appear
accepted.

### Run policy on the fixed-tick thread

Rejected because SQLite and registry access can block and would violate the
simulation-thread boundary.

### Evaluate `LOCAL_TOFU` before reserving queue capacity

Rejected because a full queue could then persist a first-use binding for a session
that the server explicitly rejects with `SERVER_CAPACITY_EXCEEDED`.

### Send acceptance before checking queue capacity

Rejected because a client could be told it was accepted while the server had no
bounded owner for the live channel.

### Use an unbounded concurrent queue

Rejected because authenticated clients could exhaust memory and connection
capacity faster than the lobby consumes sessions.

### Open a second identity runtime inside the listener

Rejected because administration, session admission, and registry reload must use
one SQLite database and one active registry store owned by the process.
