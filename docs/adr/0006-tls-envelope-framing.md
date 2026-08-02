# ADR 0006: Strict protocol-envelope framing over authenticated TLS

- Status: Accepted for the blocking framing slice
- Date: 2026-08-02
- Issues: #34, #46
- Depends on: ADR 0002, ADR 0004, ADR 0005

## Context

ADR 0002 defines a fixed 40-byte `ProtocolEnvelope` header and bounded payload,
while ADR 0005 provides an authenticated TLS 1.3 byte stream with a verified
server identity and RFC 9266 channel binding. A TLS stream does not preserve
application message boundaries. Each runtime adapter must therefore use one
strict framing implementation rather than inventing its own buffering, length
validation, session checks, or sequence behavior.

Reading the declared payload length before validating the remaining header could
allocate attacker-controlled memory. Allowing callers to choose outbound
sequence numbers also makes concurrent writers unsafe: a writer carrying
sequence one may acquire the output lock before sequence zero.

## Decision

### Shared header validation

`ProtocolCodec.frameBytesFromHeader` accepts exactly one 40-byte header. It
validates the same magic, protocol version, message type, flags, non-negative
sequence, global payload limit, and per-message payload limit used by the full
decoder. It returns the complete frame size only after those checks pass.

`ProtocolCodec.decode` reuses the same internal header parser. The streaming and
in-memory paths therefore cannot silently drift. The TLS adapter allocates the
payload buffer only after the validated frame size is known.

### Blocking envelope stream

`TlsEnvelopeStream` owns one `Tls13Connection` and one expected logical session
UUID. It is a blocking adapter primitive and must not execute on the simulation
thread.

The reader:

1. reads one byte to distinguish clean EOF from a partial header;
2. reads the remaining fixed header exactly;
3. validates the header and bounded frame size;
4. allocates and reads exactly the declared payload;
5. performs the full `ProtocolCodec.decode` validation;
6. verifies the expected session UUID;
7. verifies that the message is allowed on the reliable channel;
8. accepts only the exact next inbound sequence.

Clean EOF before a new header returns an empty result and closes the connection.
EOF inside the header or payload is a bounded `TRUNCATED_MESSAGE` failure.

### Sequence ownership

Inbound and outbound directions have independent sequences beginning at zero.
Inbound frames must be gap-free and duplicate-free.

The stream, not the caller, atomically claims outbound sequence numbers under the
write lock. `send(messageType, payload)` constructs the envelope with the fixed
session UUID and current protocol version. This permits concurrent callers
without allowing lock acquisition order to create an invalid sequence. Sequence
space exhaustion is terminal.

### Channel and session policy

A reliable stream accepts message types marked `RELIABLE` or `BOTH` and rejects a
`REALTIME`-only type. Inbound frames carrying another session UUID are rejected.
Outbound callers cannot choose a different session UUID because the stream
constructs the envelope.

### Concurrency and failure behavior

Read and write operations use separate locks, allowing one reader and one writer
to block concurrently. Multiple readers are serialized with each other, and
multiple writers are serialized with each other. No two encoded frames can
interleave on the TLS output stream.

Any I/O failure, malformed frame, session mismatch, wrong channel, duplicate or
gapped sequence, or exhausted sequence closes the TLS connection before the
error is returned. Close failures are attached as suppressed exceptions. Error
messages never include payload bytes.

A locally oversized outbound payload is rejected before a sequence is claimed or
TLS is modified.

## Validation requirements

Tests must prove:

- exact fixed-header validation returns a bounded complete frame size;
- invalid magic, type, length, truncated header, and extra header bytes fail
  before payload allocation;
- two concurrent writers receive consecutive sequence numbers and produce two
  non-interleaved frames;
- two envelopes in each direction round-trip over a real authenticated TLS
  connection;
- duplicate sequence, foreign session, and truncated payload close the stream;
- clean EOF before the next header is distinguishable from truncation;
- realtime-only policy and sequence exhaustion have explicit unit coverage;
- full repository quality and dependency-verification gates remain green.

## Consequences

- Reliable application framing has one implementation and one set of failure
  semantics.
- Payload memory is bounded before allocation.
- Session and sequence invariants are enforced below gameplay payload decoding.
- The adapter remains blocking and deliberately exposes no executor or hidden
  thread.
- Issue #34 remains open for the asynchronous `ReliableChannel`, runtime listener
  and connector ownership, admission limits, authentication message flow,
  reconnect, public PKI, and realtime transport.

## Rejected alternatives

- **Length prefix outside `ProtocolEnvelope`:** duplicates framing state and can
  disagree with the header's payload length.
- **Reading the entire TLS stream until EOF:** loses message boundaries and
  prevents long-lived sessions.
- **Allocating from the raw length field:** allows invalid headers to influence
  memory before type and per-message limits are checked.
- **Caller-assigned outbound sequence:** unsafe with concurrent writers and easy
  to misuse.
- **One global read/write lock:** prevents normal full-duplex traffic.
- **Recovering after malformed framing:** risks resynchronizing at attacker-chosen
  bytes; the connection closes fail-closed instead.
