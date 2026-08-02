# ADR 0009: Fixed-size TLS session UUID bootstrap

- Status: Accepted for the pre-envelope session slice
- Date: 2026-08-02
- Issues: #34, #53
- Depends on: ADR 0002, ADR 0004, ADR 0005, ADR 0006, ADR 0007, ADR 0008

## Context

Every `SFR1` protocol envelope contains a logical session UUID. The strict
`TlsEnvelopeStream` rejects a different UUID before delivering a payload. After
ADR 0008, the server can hand an authenticated `AcceptedTlsConnection` to a
handler, but the peers still do not share the UUID required for their first
envelope.

Generating an independent UUID on each side cannot work. Deriving the UUID
silently from the TLS exporter would conflate the logical session identifier with
the channel-binding value, make multiple logical sessions over one future channel
ambiguous, and change the identity transcript contract without an explicit wire
version.

The bootstrap must remain small, bounded and fail-closed. It is transported only
after TLS 1.3, server authentication, ALPN and exporter capture have succeeded.

## Decision

### Wire record

Before the first ordinary envelope, both sides use one fixed 28-byte big-endian
record:

| Field | Bytes | Rule |
|---|---:|---|
| magic | 4 | ASCII `SFSB` |
| schema version | 2 | unsigned value `1` |
| record type | 2 | `SESSION_OFFER=1`, `SESSION_ACCEPT=2` |
| protocol major | 2 | current `ProtocolVersion.major` |
| protocol minor | 2 | current `ProtocolVersion.minor` |
| session UUID | 16 | non-zero RFC 4122 variant 2, version 4 |

There is no length field because the record has one exact size. A decoder rejects
short, long, wrong-magic, wrong-schema, wrong-type, unsupported-protocol and
invalid-UUID records before constructing a reliable channel. Error messages do
not contain record bytes.

The public v1 vector uses session UUID
`11111111-2222-4333-8444-555555555555`:

```text
SESSION_OFFER
53465342000100010001000011111111222243338444555555555555

SESSION_ACCEPT
53465342000100020001000011111111222243338444555555555555
```

### Role and exchange order

The server is the sole session-ID authority for this exchange:

1. the server generates 16 bytes with its injected `SecureRandom`;
2. it sets the RFC 4122 version-4 and variant-2 bits;
3. it writes and flushes `SESSION_OFFER`;
4. the client validates the offer;
5. the client writes and flushes `SESSION_ACCEPT` with exactly the same UUID;
6. the server validates the accept and exact UUID equality;
7. both peers create their `TlsEnvelopeStream` and `AsyncTlsReliableChannel`.

The explicit record types prevent reflection or role confusion. The client cannot
select a different UUID. A changed accept UUID closes the connection and returns
server listener admission.

The protocol intentionally has no third ready message. Once the client has
written an exact echo, any later server-side I/O failure is observed by the first
ordinary channel operation. Adding a third message would require another version
and does not strengthen the authenticated TLS record itself.

### Security relationship

The bootstrap does not add a custom MAC, signature or encryption layer. The
record is already confidential and integrity-protected by the authenticated TLS
1.3 channel established in ADR 0005.

The UUID is not treated as a secret or authenticator. Identity Proof V2 later
signs the agreed session UUID together with the pinned `ServerId` and the exact
32-byte RFC 9266 `tls-exporter` channel binding. Relaying a proof onto a different
TLS connection therefore fails even if an attacker learns or replays the UUID.

The bootstrap never substitutes the UUID for the exporter, certificate key or
server identity.

### Timeouts

Bootstrap reads use an explicit timeout from 1 millisecond through 30 seconds;
the default is 5 seconds. Direct and provider-wrapped `SocketTimeoutException`
instances are normalized to the semantic `TIMEOUT` code.

The client and server set this timeout before the first bootstrap record. After a
successful accept, the underlying socket read timeout is reset to zero. This
prevents the short handshake/bootstrap timeout from becoming an unintended idle
session timeout. Runtime liveness and heartbeat policy belongs to a later layer.

A timeout, truncation, validation failure, I/O failure or runtime construction
failure closes the TLS owner. On the server side the owner is the full
`AcceptedTlsConnection`, so listener admission is returned exactly once.

### Ownership transfer

`TlsSessionBootstrap.acceptServerSession` consumes an
`AcceptedTlsConnection`. After success, its ownership is held by a
`TlsEnvelopeStream` close action. Closing the resulting async reliable channel
therefore closes TLS and the lease rather than closing only the raw protocol
object.

`TlsSessionBootstrap.connectClientSession` similarly transfers the client
`Tls13Connection` into the envelope stream.

`Tls13Connection.close()` is idempotent and preserves both protocol-close and
socket-close failures. This permits defensive cleanup by callers without causing
a second provider close.

The result is `BootstrappedReliableSession`, which exposes:

- the agreed UUID;
- `Tls13SessionSecurity`, including `ServerId` and `SecureChannelBinding`;
- the bounded asynchronous `ReliableChannel`;
- an asynchronous idempotent close operation.

Its public string form does not expose channel-binding bytes.

## Validation requirements

Unit tests must prove:

- exact 28-byte offer and accept vectors;
- strict validation of size, magic, schema, type, protocol and UUID properties;
- bounded error messages without raw records;
- timeout bounds and static initialization safety;
- generated IDs are non-zero, unique in the test sample, version 4 and RFC variant
  2.

A real listener/client TLS loopback must prove:

- both peers receive the same UUID;
- both peers expose the same server identity and channel binding;
- socket read timeouts are zero after success;
- first envelopes in both directions use the agreed UUID and sequence zero;
- closing both sessions returns listener active admission;
- a changed accept UUID fails with `SESSION_MISMATCH`, closes the lease and permits
  a later valid session;
- an unanswered offer fails with `TIMEOUT`, closes the lease and permits a later
  valid session.

The complete repository quality gate remains required. No new external dependency
is introduced.

## Consequences

- An authenticated listener lease can now become the same strict async reliable
  session on both peers.
- Identity Proof V2 receives its exact session, server and channel-binding values
  without another conversion layer.
- The pre-envelope wire surface is only 28 fixed bytes per direction.
- Short bootstrap timeouts no longer leak into normal idle sessions.
- The server remains the sole authority for logical session IDs.
- Identity payload codecs, proof orchestration, runtime command delivery, client
  reconnect and realtime transport remain separate work.

## Rejected alternatives

- **Independent random UUIDs:** guaranteed session mismatch.
- **UUID derived from `tls-exporter`:** conflates logical identity with channel
  binding and changes semantics without a versioned record.
- **Client-selected UUID:** gives the unauthenticated client authority over server
  session identifiers and complicates collision policy.
- **Variable-length JSON or Java serialization:** creates unnecessary parsing,
  allocation and compatibility surface before the strict envelope codec.
- **Custom MAC or signature:** duplicates TLS integrity and risks inventing a new
  cryptographic protocol.
- **No role-specific record type:** permits reflection and ambiguous state.
- **Keeping the bootstrap timeout after success:** turns a handshake defense into
  an accidental idle disconnect policy.
- **Closing only raw server TLS:** leaks the listener active-connection permit.
