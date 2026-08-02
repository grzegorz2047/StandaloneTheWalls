# TLS identity admission integration

This document describes the server-side boundary introduced by issue #76. It is a
developer and operator contract for connecting the authenticated reliable TLS path
to the one process-owned `LocalIdentityRuntime`. It does not provision production
certificates, start a public listener, or implement lobby membership.

## Required ordering

A player connection may reach the future lobby only through this sequence:

1. TLS 1.3 authenticates and pins the server.
2. The TLS bootstrap agrees one session UUID.
3. Identity Proof V2 verifies possession of the player Ed25519 private key using
   the transport-derived server ID, session ID, and RFC 9266 channel binding.
4. `TlsIdentityAdmissionGateway` calls the existing
   `LocalIdentityRuntime.admit(canonicalHandle, playerId)`.
5. The runtime checks the stable player-ID ban before evaluating the selected
   `LOCAL_TOFU`, `GLOBAL_ONLY`, or `HYBRID` handle policy.
6. The gateway reserves bounded pre-lobby queue capacity.
7. The server sends one `SESSION_ADMISSION_RESULT`.
8. An accepted `AuthorizedPlayerSession` is committed to the reserved queue slot.
9. A future lobby poll or drain transfers ownership from the queue.

`IDENTITY_RESULT=ACCEPTED` means only that the cryptographic proof succeeded. It
must never be interpreted as handle authorization or lobby admission.

## Process ownership

Construct exactly one `LocalIdentityRuntime` for the server process. Pass that same
instance to:

- local identity administration;
- registry reload and verification;
- `TlsIdentityAdmissionGateway`.

Do not open a second SQLite database connection graph or a second active registry
store inside a listener handler. Doing so would let administration and player
admission observe different bindings, bans, or registry snapshots.

A production process that has no explicitly provisioned TLS listener remains
network-disabled. This slice does not generate certificates, silently open a port,
or create an unauthenticated fallback. Production certificate loading, listener
configuration, public PKI, reconnect, realtime transport, and actual lobby wiring
remain separate work under the secure transport epic.

## Pre-lobby queue

`AuthorizedPlayerSessionQueue` is a bounded, non-blocking ownership boundary.
Capacity must be between 1 and 10,000.

Admission reserves a slot before sending an accepted status. A reservation counts
toward capacity and can be committed or cancelled exactly once. A full queue
returns `SERVER_CAPACITY_EXCEEDED`; it never blocks a transport worker waiting for
the simulation or lobby.

The queue owns committed sessions until `poll()` or `drain(maximumSessions)`
returns them. The caller then owns every returned session and must close it when
lobby/session processing ends. Closing the queue closes all sessions that have not
been transferred.

The fixed-tick thread may poll or drain already-authorized sessions, but it must
not perform TLS bootstrap, Identity Proof, SQLite access, registry verification, or
wait for channel close.

## Authorized session fields

`AuthorizedPlayerSession` exposes only bounded data required by later runtime work:

- session UUID;
- authenticated server ID;
- verified player ID;
- canonical handle;
- `LOCAL_UNVERIFIED` or `GLOBAL_VERIFIED`;
- the open post-identity reliable channel.

It deliberately does not expose private keys, raw proofs, nonce bytes, exporter
bytes, registry artifacts, audit records, IP addresses, or provider exceptions.

## Admission result payload

The reliable `SESSION_ADMISSION_RESULT` payload v1 is exactly two bytes:

| Byte | Meaning |
| --- | --- |
| 0 | schema version, currently `1` |
| 1 | unsigned stable admission status |

Accepted statuses:

| Status | Meaning |
| --- | --- |
| `GLOBAL_ACCEPTED` | Active global registry entry matches the player ID. |
| `LOCAL_FIRST_USE_ACCEPTED` | Local TOFU binding was created atomically. |
| `LOCAL_RETURNING_ACCEPTED` | Existing local binding matches the player ID. |

Policy rejections:

| Status | Meaning |
| --- | --- |
| `PLAYER_BANNED` | Stable player ID is locally banned. |
| `REGISTRY_UNAVAILABLE` | Required registry state is absent. |
| `REGISTRY_STALE` | Required registry state is older than policy allows. |
| `UNKNOWN_GLOBAL_HANDLE` | Strict global mode has no entry for the handle. |
| `REVOKED_GLOBAL_HANDLE` | The global entry is revoked. |
| `GLOBAL_PLAYER_MISMATCH` | The global handle belongs to another player ID. |
| `LOCAL_BINDING_CONFLICT` | The local handle is bound to another player ID. |
| `LOCAL_BINDING_CAPACITY_EXCEEDED` | A first-use binding cannot be persisted within configured limits. |

Operational rejections:

| Status | Meaning |
| --- | --- |
| `SERVER_CAPACITY_EXCEEDED` | No pre-lobby queue slot can be reserved. |
| `SERVER_SHUTTING_DOWN` | Gateway lifecycle stopped accepting ownership. |

The payload never contains exception messages or database details. A rejected
result is followed by channel close.

## Lifecycle and shutdown

`TlsIdentityAdmissionGateway` owns named virtual worker threads for bootstrap,
proof, policy, result send, and handoff. The listener accept thread only transfers
an already authenticated TLS lease to the gateway.

On close, the gateway:

1. changes state to closing and rejects new leases;
2. interrupts workers and closes every tracked in-flight TLS connection;
3. waits for the workers for the configured bounded timeout, allowing each queue
   reservation to commit or cancel exactly once;
4. closes every queued, untransferred authorized session;
5. becomes closed.

This order prevents a completed accepted result from losing its reserved handoff
slot to a concurrent queue close. If the TLS lease is closed before result send
completes, the send fails and the reservation is cancelled.

The gateway checks lifecycle after bootstrap and after Identity Proof, before
calling the SQLite-backed runtime. No new policy mutation begins after close has
claimed ownership.

Connection and channel close operations are idempotent. Closing a rejected or
failed session releases the listener active permit exactly once through the
transport-owned accepted connection.

## Diagnostics

The optional event observer receives only:

- one bounded gateway event code;
- optionally one stable admission status.

It does not receive handles, player IDs, addresses, payloads, signatures, registry
digests, exception text, or SQLite paths. Observer exceptions are ignored and
cannot alter admission or resource ownership.

## Test coverage

The integration test uses a real loopback TLS 1.3 listener and client and covers:

- `LOCAL_TOFU` first use, returning identity, and conflicting key;
- `GLOBAL_ONLY` accepted and unknown global handles;
- `HYBRID` global and local guest admission;
- ban-before-binding, proving a banned first-use attempt does not reserve a handle;
- bounded pre-lobby queue overflow;
- gateway shutdown closing queued leases and releasing listener capacity.

Existing policy tests retain the complete matrix for missing, stale, revoked,
mismatched, and capacity-limited identity decisions. The new typed mapping test
proves that every existing semantic decision has one stable wire status.
