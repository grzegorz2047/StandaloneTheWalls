# ADR 0040: DTLS 1.3 realtime channel with one-time external-PSK tickets

- Status: Accepted for the realtime ticket foundation
- Date: 2026-08-04
- Decision owners: Sunderfront maintainers
- Related: #13, #22, #29, #34, #161
- Depends on: ADR 0005 through ADR 0010

## Context

Sunderfront already uses TLS 1.3 over TCP for reliable Direct Connect. That path
provides server pinning or TOFU, RFC 9266 `tls-exporter` channel binding, strict
session bootstrap, Identity Proof V2, authorization policy and bounded reliable
framing. A future realtime path needs datagram delivery for movement snapshots and
other latency-sensitive state without weakening that authenticated boundary.

The realtime credential must not be a long-lived bearer token. A token copied from
a log, database, old packet capture or previous round must not establish a later
channel. The server also needs a bounded process-local owner for credentials that
exist between reliable authentication and a future datagram handshake.

This slice decides the transport and provisioning model and implements only the
one-time credential store. It deliberately does not open a UDP socket or define
realtime gameplay payloads.

Relevant standards are:

- TLS 1.3 and external PSKs: RFC 8446;
- DTLS 1.3: RFC 9147;
- guidance for external PSKs in TLS and DTLS 1.3: RFC 9257;
- QUIC transport: RFC 9000;
- TLS 1.3 channel binding: RFC 9266.

## Options considered

### TLS 1.3 over the existing TCP connection

TLS 1.3 remains the correct reliable/control transport. It provides ordered,
non-replayed delivery and already owns identity, admission, lobby commands and
phase transitions. Using the same ordered byte stream for all realtime traffic
would, however, allow one delayed or retransmitted segment to block later movement
state. The reliable channel remains mandatory, but it is not selected as the only
future realtime carrier.

### DTLS 1.3 over UDP

DTLS 1.3 reuses the TLS 1.3 handshake and key schedule while preserving datagram
semantics. It supports external PSKs provisioned out of band and can use a cookie
exchange for source-address validation. It does not provide application ordering,
and its per-record replay protection is not a replacement for gameplay sequence
validation. These properties match an authoritative snapshot channel when both
layers are explicit.

Bouncy Castle already supplies the project's low-level TLS adapter, so it was the
first provider evaluated for the future DTLS adapter. The exact pinned 1.84
release does not yet implement the DTLS 1.3 server path; ADR 0042 therefore gates
production ticket issuance and forbids a DTLS 1.2 fallback. DTLS 1.3 remains the
selected protocol decision, not a claim that the current provider can run it.

### QUIC

QUIC provides encrypted UDP transport, streams, loss recovery and path migration.
It is a viable future adapter, especially if the project later wants one transport
for reliable streams and datagrams. Adopting it now would duplicate the already
working TLS reliable path, add connection/path-migration policy before the game
has an authoritative realtime message contract and require a separately maintained
implementation stack. QUIC is deferred, not prohibited. A future adapter must
preserve the contracts and authority boundaries defined here.

## Decision

### Reliable channel remains the provisioning authority

A realtime ticket may be issued only after the server owns an authenticated and
authorized reliable session. Ticket issuance is a reliable protocol operation; it
never occurs from an unauthenticated UDP packet.

The process-local server record binds the credential to:

- the stable `ServerId`;
- the non-zero reliable session UUID;
- the authenticated `PlayerId`;
- the SHA-256 digest of the exact 32-byte RFC 9266 channel binding;
- a non-negative round epoch;
- one expiry instant.

The channel-binding digest is association context, not secret key material. The
external PSK is generated independently from cryptographic entropy and is delivered
only inside the existing confidential TLS channel.

### External-PSK material

Each ticket contains:

- a uniformly random 128-bit opaque PSK identity;
- a uniformly random 256-bit external PSK;
- an absolute expiry instant.

The identity is a lookup label, not an authenticator. The future DTLS 1.3 handshake
must prove possession of the PSK through the standard PSK binder/key schedule. The
application must not add a custom MAC, cipher, KDF or signature around this ticket.

The identity may be visible during a DTLS handshake and therefore contains no
player ID, handle, session UUID, endpoint, server ID, timestamp or sequence.

### One-time redemption

The server stores a defensive copy of the PSK in a bounded process-local map. A
future DTLS handshake reserves a credential by atomically removing its identity
from the map before receiving the secret. Exactly one concurrent caller can obtain
`REDEEMED`. Every later lookup returns `UNKNOWN_OR_REPLAYED`.

An expired record is removed and destroyed and returns `EXPIRED` on that first
lookup. A subsequent lookup is indistinguishable from unknown or replayed input.
The store intentionally does not reveal whether an arbitrary identity once existed.

A failed, cancelled or timed-out future DTLS handshake does not put the ticket back.
The client may request another ticket only over an authenticated reliable session.
This prevents handshake retries from becoming replayable credentials.

### Bounds and lifecycle

The store has hard limits for active tickets and maximum lifetime. Issuance first
removes expired records, then fails with `CAPACITY_EXHAUSTED` rather than evicting a
valid credential. Identity generation has a bounded collision retry count.

Clock and entropy are explicit dependencies. Production uses UTC and
`SecureRandom`; deterministic tests inject both. Entropy returning a null or
incorrectly sized value fails before any ticket is inserted.

Closing the store is idempotent, destroys every retained PSK and rejects later
issue, redeem, cleanup and count operations. Issued client material and redeemed
handshake material have separate `AutoCloseable` ownership and redacted public
text. Arrays are copied at every public boundary.

### Future DTLS requirements

The later socket/handshake slice must additionally enforce:

- DTLS 1.3 only and an explicit AEAD cipher allowlist;
- no 0-RTT application data;
- a bounded stateless cookie/address-validation stage before expensive state;
- bounded concurrent handshakes, active channels, packet sizes and timeouts;
- the implementation's DTLS record replay window enabled;
- application-level session/round identifiers and monotonic packet or snapshot
  sequences, because DTLS datagrams may be reordered;
- no fixed-tick socket I/O or cryptography;
- deterministic shutdown and PSK destruction;
- no NAT rebinding or path migration until a separate authenticated reachability
  decision exists.

## Security consequences

### Positive

- The realtime credential is provisioned through the already authenticated server
and player identities.
- A captured old identity or repeated handshake cannot obtain the same PSK twice
from the server store.
- Expiry and process-local storage bound the value of leaked client ticket material.
- The future handshake uses the standard DTLS 1.3 PSK proof and key schedule rather
than project cryptography.
- Reliable session, player and round context remain available to the authoritative
server after redemption.

### Limitations

- One-time redemption prevents replay after the first reservation but cannot stop a
thief with the PSK from winning a race before the legitimate client. PSK secrecy
therefore depends on TLS provisioning and client memory protection.
- This store does not mitigate spoofed-source UDP floods; the future listener still
needs DTLS cookie/address validation and admission limits.
- DTLS does not make unordered gameplay state ordered. The application still needs
bounded sequence and freshness rules.
- Process restart invalidates all outstanding tickets by design.
- Reconnect, NAT rebinding, migration and resumption remain separate decisions.

## Validation requirements

Tests must prove:

- issue and redeem preserve exact immutable context and expiry;
- identity, PSK and channel-binding arrays are defensive copies;
- the exact expiry boundary rejects the ticket;
- replay and unknown identities share one public result;
- two concurrent redemptions have exactly one winner;
- capacity failure does not evict a valid ticket;
- expired cleanup permits later issuance;
- invalid lifetimes, entropy lengths and repeated identity collisions fail closed;
- close is idempotent and rejects every later operation;
- public text never contains PSK bytes.

The complete repository quality gate remains required.

## Alternatives rejected

- **Long-lived bearer token over UDP:** possession alone would allow replay and
would not use a standard secure-channel proof.
- **Derive the DTLS PSK directly from the RFC 9266 channel binding:** RFC 9266
explicitly defines channel binding as non-secret context and forbids treating it as
privileged key material.
- **Sign a custom UDP challenge with the player's Ed25519 key:** repeats identity
protocol work and still leaves encryption, replay windows and key derivation to a
custom design.
- **Reusable PSK per player or server:** compromise would survive sessions, rounds
and reconnects.
- **Restore a ticket after failed handshake:** creates a replay window and ambiguous
concurrent ownership.
- **Evict the oldest valid ticket at capacity:** turns load into nondeterministic
credential invalidation.
- **Use QUIC immediately:** adds a second reliable transport and path-migration
policy before the realtime domain contract exists.

## Follow-up

- Reliable request/result provisioning and send-failure revocation are defined by
  ADR 0041 and issue #163.
- Resolve the provider capability gate in ADR 0042, then implement bounded DTLS
  1.3 cookie, handshake and active-channel ownership using this store.
- Define authoritative realtime envelopes, round/session binding, monotonic
sequences and snapshot freshness.
- Add reconnect/resume and NAT rebinding only through separate threat-modelled ADRs.
- Add the public-PKI/domain certificate trust adapter for reliable TLS separately.
