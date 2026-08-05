# ADR 0041: Provision one-time realtime tickets through the admitted reliable session

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Sunderfront maintainers
- Related: #34, #55, #76, #88, #161, #163
- Depends on: ADR 0010, ADR 0028, ADR 0034 and ADR 0040

## Context

ADR 0040 selected DTLS 1.3 with externally provisioned PSKs and introduced a
bounded process-local one-time ticket store. It deliberately did not define how a
client requests a credential or how the server derives the trusted ticket context.

The reliable TLS session already has all required authority: server identity,
non-zero session UUID, authenticated player identity and the exact RFC 9266
channel binding. After admission, the minimal lobby owns the only receive loop for
that reliable channel and the authoritative match state exposes the current round
number. Adding another request reader would race the lobby and break ordered
message ownership.

## Decision

### Exact reliable messages

Two reliable message types are added without renumbering the existing catalog:

- `REALTIME_TICKET_REQUEST` (`17`);
- `REALTIME_TICKET_RESULT` (`18`).

All integers are big-endian. Schema version is `1`.

A request is exactly 10 bytes:

| Offset | Size | Field |
|---:|---:|---|
| 0 | 1 | schema version |
| 1 | 1 | positive realtime profile version |
| 2 | 8 | positive request ID |

It contains no player ID, server ID, session ID, channel binding, round, lifetime,
endpoint or entropy supplied by the client.

An issued result is exactly 67 bytes:

| Offset | Size | Field |
|---:|---:|---|
| 0 | 1 | schema version |
| 1 | 1 | status `ISSUED` |
| 2 | 1 | selected profile version |
| 3 | 8 | echoed request ID |
| 11 | 8 | positive expiry epoch milliseconds |
| 19 | 16 | opaque external-PSK identity |
| 35 | 32 | external PSK |

A rejected result is exactly 12 bytes and contains schema, status, profile,
stable public rejection code and echoed request ID. Rejections do not expose
capacity, active-ticket count, entropy failures, collision state or exceptions.
Unknown versions, lengths, status values, rejection values, non-positive IDs,
truncation and trailing bytes are rejected before ownership transfer.

### Authority and context derivation

The request is accepted only by the existing lobby-owned receive loop after
Identity Proof V2 and server identity admission. The process-owned provisioner
derives every trusted field locally from `AuthorizedPlayerSession` and the
current authoritative match snapshot:

- `ServerId`;
- reliable session UUID;
- authenticated `PlayerId`;
- SHA-256 of the exact 32-byte RFC 9266 channel binding;
- positive round number used as the ticket epoch.

The server selects a bounded lifetime from process configuration. The client
cannot request a lifetime, epoch or identity association.

### Request ordering and rate policy

Realtime ticket requests share the session's positive monotonic request-ID space
with lobby commands. A replayed or lower ID is a protocol violation and closes
the session. The lobby coordinator serializes commands, so a session cannot have
two concurrent issue operations.

The selected bounded issuance policy is one successfully delivered ticket per
reliable session per round. A later request in the same round receives
`ALREADY_ISSUED_FOR_ROUND`. When the authoritative round changes, an unused old
ticket is revoked before a replacement is issued. This is both the rate limit and
the single-active-credential rule for this slice.

### Send, revocation and ownership

The process owns one `RealtimeTicketProvisioner` and one underlying
`OneTimeRealtimeTicketStore`. Lobby members do not construct stores.

Issuance creates two independent secret owners:

1. the store retains the server-side copy for a future one-time DTLS redeem;
2. `IssuedRealtimeTicket` owns the temporary copy encoded into the reliable result.

The result payload is sent only through the authenticated TLS channel. After send
completion, the temporary PSK and encoded payload are zeroed and the issued owner
is closed. If send fails, times out or is interrupted, the newly created store
record is revoked and destroyed before the session is removed. Session removal,
lobby shutdown and round replacement also revoke any still-owned identity.

The client decoder returns a `RealtimeTicketResult`. Its issued variant contains
one `ClientRealtimeTicket` owner with defensive copies, redacted text and
idempotent PSK destruction. `ConnectedLobbySession` correlates exactly one
request through its existing receiver and transfers that owner to the caller.
Malformed or uncorrelated results fail closed; an unexpected decoded ticket is
closed before the session terminates.

### Threading and shutdown

Network receive and ticket provisioning run on the lobby's named virtual worker
and coordinator threads. They do not execute on the fixed-tick simulation thread
or renderer thread. The fixed tick only publishes authoritative match progress.

Server shutdown stops reliable admission, closes the lobby and its sessions, then
closes the process-owned provisioner. Closing the provisioner closes the store and
zeroes every retained PSK.

## Security consequences

### Positive

- Client-controlled bytes cannot override trusted ticket context.
- Exactly one receive owner preserves reliable ordering and avoids message races.
- One successful credential per session/round bounds issuance without a wall-clock
  rate limiter.
- Failed delivery cannot leave an unknown usable credential in the store.
- Client, temporary server and retained server copies have explicit independent
  destruction ownership.
- Public results and diagnostics do not contain raw secret material or internal
  store details.

### Limitations

- This slice does not open UDP or perform a DTLS handshake.
- A delivered client ticket remains valuable until expiry or one-time redemption;
  client process memory protection remains required.
- The one-per-round policy deliberately rejects replacement after a successful
  delivery in the same round, even if the client destroys its copy.
- Endpoint selection, stateless cookies, spoofed-source protection, realtime
  packets, reconnect and NAT rebinding remain deferred.

## Validation requirements

Tests must prove:

- exact request, issued-result and rejected-result layouts and all malformed cases;
- client defensive copies, redaction and idempotent secret destruction;
- context derivation from the admitted session and authoritative round;
- a successful real TLS + identity + admission + lobby request/result loopback;
- same-round second request rejection and monotonic request IDs;
- send failure revocation and zero active retained tickets;
- session removal and shutdown revocation;
- store revocation is idempotent and redemption after revocation is unknown;
- no ticket I/O or store work occurs on the fixed-tick or renderer thread.

The complete repository quality gate remains required.

## Follow-up

- Compose a bounded DTLS 1.3 listener with stateless cookie validation and the
  one-time external-PSK lookup.
- Define realtime application envelopes, sequencing, freshness and snapshot rules.
- Add reconnect, resume and NAT rebinding only through separate threat-modelled
  decisions.
