# ADR 0010: Identity Proof V2 over the bootstrapped reliable channel

- Status: Accepted for the cryptographic identity exchange slice
- Date: 2026-08-02
- Issues: #34, #55
- Depends on: ADR 0003, ADR 0004, ADR 0005, ADR 0006, ADR 0007, ADR 0008, ADR 0009

## Context

ADR 0004 defines the channel-bound Identity Proof V2 transcript. ADR 0009 gives
both peers the same logical session UUID and an authenticated bounded
`ReliableChannel`. The remaining ambiguity is the application exchange itself:
how challenge, proof and result are encoded, ordered, timed out and owned.

Without one strict state machine, two implementations could serialize the same
domain values differently, accept a result before a challenge, verify a proof
against peer-provided transport context, run two simultaneous exchanges, leave a
challenge outstanding after timeout or deliver identity messages to the game
runtime after authentication.

## Decision

### Local security context

The wire challenge does **not** contain `ServerId`, session UUID or
`SecureChannelBinding`. Both peers already possess those values from their local
`BootstrappedReliableSession`.

The server issues the domain challenge with exactly:

- `session.security().serverId()`;
- `session.sessionId()`;
- `session.security().channelBinding()`.

The client reconstructs the signing challenge with the same three local values
plus the received nonce and expiration. Peer-controlled payload bytes therefore
cannot replace the pinned server identity, logical session or TLS exporter.

### Payload schema v1

All values are big-endian. Every payload begins with unsigned 16-bit schema
version `1`. Java native serialization and JSON are forbidden.

#### `IDENTITY_CHALLENGE`

Fixed 42 bytes:

| Field | Bytes | Rule |
|---|---:|---|
| schema | 2 | `1` |
| nonce | 32 | fresh server challenge nonce |
| expires-at | 8 | non-negative epoch milliseconds through year 9999 |

#### `IDENTITY_PROOF`

Bounded variable length:

| Field | Encoding | Rule |
|---|---|---|
| schema | u16 | `1` |
| protocol major/minor | u16 + u16 | signed transcript version |
| canonical handle | u16 length + ASCII | 3–24 bytes, `[a-z0-9_]+` |
| player ID | u16 length + ASCII | exactly 56 bytes, canonical `sf1_...` |
| public key | u16 length + bytes | 1–256 bytes; canonical Ed25519 SPKI required |
| signature | u16 length + bytes | exactly 64 Ed25519 bytes |

The decoder validates field bounds and canonical public-key encoding before
calling the domain verifier. Trailing bytes are rejected.

#### `IDENTITY_RESULT`

Bounded variable length:

| Field | Encoding | Rule |
|---|---|---|
| schema | u16 | `1` |
| status | u16 | stable catalog ID |
| public code | u16 length + ASCII | must exactly match the catalog entry |

No exception message, stack trace, nonce, signature, key or payload fragment is
sent. Stable public statuses include accepted, domain-verification failures,
malformed proof, unexpected message and internal error.

### Exchange order

Server:

1. claims the session's only identity exchange;
2. issues and sends one challenge;
3. receives exactly one proof;
4. consumes the challenge before signature verification;
5. sends exactly one result;
6. returns an authenticated session only for `ACCEPTED`.

Client:

1. claims the session's only identity exchange;
2. receives challenge as the first identity message;
3. rejects an already-expired challenge;
4. signs Identity Proof V2 using local transport context;
5. sends exactly one proof;
6. receives exactly one result;
7. returns an authenticated session only for `ACCEPTED`.

A result before challenge, another message type, EOF, malformed payload, timeout,
rejection or channel failure is terminal. An unfinished server challenge is
explicitly discarded rather than occupying ledger capacity until expiration.

### Execution and timeouts

Each exchange runs on a named Java 21 virtual thread. The caller and fixed-tick
simulation thread never block on reliable I/O or shutdown.

Configuration has three independent bounded durations:

- per-step timeout: 1 ms–30 s, default 5 s;
- whole-exchange timeout: 1 ms–60 s, default 15 s and never shorter than a step;
- close wait: 1 ms–30 s, default 3 s.

The earlier of the step timeout and remaining whole-exchange deadline applies to
each operation. A terminal failure starts session close and preserves close
failures as suppressed causes.

### Ownership after success

`AuthenticatedReliableSession` exposes:

- the original session UUID and TLS security metadata;
- verified `PlayerId` and canonical handle;
- a post-authentication `ReliableChannel`.

The post-authentication channel rejects and closes on any later
`IDENTITY_CHALLENGE`, `IDENTITY_PROOF` or `IDENTITY_RESULT`, whether sent locally
or received from the peer. Ordinary envelope sequence numbers continue from the
identity exchange rather than restarting.

Authorization is intentionally separate. A valid signature proves possession of
the player key; `LOCAL_TOFU`, `GLOBAL_ONLY` and `HYBRID` decide whether that
identity and handle may join.

## Public vectors

Canonical challenge:

- nonce bytes `00` through `1f`;
- expiration `2025-01-01T00:00:00Z` (`1735689600000` ms);
- encoded hex:
  `0001000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f000001941f297c00`.

Canonical proof fixture:

- protocol `1.0`;
- handle `player_one`;
- player ID
  `sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua`;
- public key
  `MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=`;
- signature: 64 zero bytes for codec layout testing only;
- encoded length: 188 bytes;
- SHA-256:
  `3d4f5c6b82fa7f81e9a98e18b17aca339691735fc2945794156eda4b4f304a2a`.

Canonical accepted result:

- encoded hex: `0001000100086163636570746564`.

The zero signature fixture is not a valid authentication proof and is never used
as a positive cryptographic vector. Real loopback tests generate and verify an
actual Ed25519 signature.

## Validation requirements

Unit and integration tests must prove:

- exact wire vectors and bounds;
- unsupported schema, non-ASCII canonical fields, invalid key, invalid lengths,
  trailing bytes, unknown status and status/code mismatch fail before verification;
- result-before-challenge, timeout, EOF and repeated exchange fail closed;
- a full listener → TLS 1.3 → session bootstrap → Identity Proof V2 exchange
  succeeds;
- the verifier uses local server/session/exporter context;
- replaying a valid proof into another TLS session is rejected;
- successful application messages continue the existing sequence;
- later identity messages close the post-authentication channel;
- timeout and rejection release listener admission exactly once;
- unfinished challenges are discarded;
- the complete repository quality gate passes without new dependencies.

## Consequences

- The first reliable transport can now establish cryptographic player identity
  without trusting context supplied by the peer.
- Wire compatibility is independently testable outside Java.
- Replay across sessions or TLS channels fails through the existing V2 transcript.
- The game runtime receives only post-authenticated channels and cannot
  accidentally process a second identity exchange.
- Handle authorization, persistence, lobby admission and fixed-tick command
  delivery remain separate work.

## Rejected alternatives

- **Sending server/session/exporter in the challenge payload:** duplicates trusted
  local context and invites verifier confusion.
- **JSON:** unnecessary parsing, allocation and canonicalization surface.
- **Free-form result messages:** unstable compatibility and accidental leakage.
- **Restarting sequence numbers after authentication:** violates one ordered
  reliable stream.
- **Leaving failed challenges until expiration:** consumes bounded ledger capacity
  after the owning session has already ended.
- **Allowing identity messages after success:** permits state-machine re-entry and
  ambiguous authorization state.
