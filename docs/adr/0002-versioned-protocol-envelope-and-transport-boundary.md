# ADR 0002: Versioned protocol envelope and transport boundary

- Status: Accepted
- Date: 2026-08-01
- Issues: #22, #29, #34

## Context

Sunderfront needs an authoritative server and two classes of delivery: reliable,
ordered messages for control/state transitions and low-latency messages for
realtime simulation. The renderer-independent protocol must not depend on a
particular socket library, Java native serialization, or executable object graphs.

Choosing TCP, UDP, QUIC, TLS, or DTLS in the same change would make the stable
message contract depend on an adapter that has not yet been benchmarked or security
reviewed. Conversely, leaving the byte envelope unspecified would allow each
adapter to invent incompatible framing and validation rules.

## Decision

Protocol v1 uses a strict, fixed 40-byte big-endian header followed by a bounded
payload:

| Field | Bytes | Rule |
|---|---:|---|
| magic | 4 | ASCII-equivalent `SFR1` marker |
| protocol major | 2 | unsigned short |
| protocol minor | 2 | unsigned short |
| message type | 2 | explicit catalog ID |
| flags | 2 | zero until a future version defines bits |
| session UUID | 16 | one logical transport session |
| sequence | 8 | non-negative signed long in v1 |
| payload length | 4 | non-negative and bounded globally and per type |

The decoder rejects an unsupported version, unknown type, unknown flag, negative
sequence, invalid length, truncated message, and trailing bytes before any payload
codec is invoked. Payload schemas will be versioned separately by the issue that
introduces each message. Java `ObjectInputStream`/`ObjectOutputStream` are forbidden.

The protocol module exposes `ReliableChannel`, `RealtimeChannel`, and
`TransportSession` interfaces only. Concrete networking and cryptographic channel
selection remain adapters. The authentication transcript from #29 and secure
channel binding from #34 will extend the message catalog without changing this
dependency direction.

## Consequences

- Core protocol tests run without a network stack or graphics device.
- Malformed framing has one bounded failure surface before gameplay decoding.
- Message IDs and maximum payload sizes require deliberate review and cannot be
  inferred from Java class names.
- A fixed header costs 40 bytes per message; realtime batching may be introduced
  later only through an explicitly versioned message type.
- Exact protocol-version compatibility is intentionally conservative for the
  first implementation. A negotiated compatibility range needs a later ADR and
  test vectors rather than silent acceptance.

## Deferred decisions

- concrete reliable/realtime transport libraries;
- TLS 1.3, DTLS 1.3, QUIC, certificate, and channel-binding implementation;
- compression and fragmentation policy;
- payload serialization format for gameplay messages;
- packet batching, acknowledgement, and snapshot-delta semantics.
