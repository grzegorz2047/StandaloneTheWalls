# ADR 0005: Bouncy Castle TLS 1.3 reliable transport adapter

- Status: Accepted for the first reliable-transport slice
- Date: 2026-08-02
- Issues: #34
- Depends on: ADR 0002, ADR 0003, ADR 0004

## Context

Sunderfront needs a secure, reliable connection before application-level player
identity messages are exchanged. Java 21's standard JSSE API can negotiate TLS
1.3, but it does not expose a public TLS exporter API that can produce the RFC
9266 `tls-exporter` channel binding required by ADR 0004.

The project must not invent an encryption protocol, silently trust a changed
server key, require a central Sunderfront certificate authority, or introduce a
native runtime dependency before the client and dedicated server have a stable
cross-platform baseline.

## Decision

The first reliable-transport adapter uses Bouncy Castle Java 1.84:

- `bcprov-jdk18on` for cryptographic primitives;
- `bctls-jdk18on` for the BCJSSE TLS provider and channel-binding API;
- `bcpkix-jdk18on` only in tests to generate short-lived self-signed fixtures.

The adapter is isolated in the renderer-independent `transport-bctls` module.
Core protocol and game-domain modules remain independent of Bouncy Castle and
concrete sockets.

### Provider isolation

The adapter constructs `BouncyCastleProvider` and
`BouncyCastleJsseProvider` instances explicitly and passes the JSSE provider to
`SSLContext.getInstance`. It does not modify the process-wide provider order via
`Security.addProvider` or security property files.

### Negotiation policy

The adapter permits only:

- TLS 1.3;
- ALPN `sunderfront/1`;
- `TLS_AES_128_GCM_SHA256`;
- `TLS_CHACHA20_POLY1305_SHA256` when supported by the provider;
- `TLS_AES_256_GCM_SHA384` when supported by the provider.

The allowed list is intersected with the actual provider capabilities. An empty
intersection fails before the handshake. TLS 1.2 and earlier, missing or changed
ALPN, and any negotiated cipher outside the allowlist are rejected.

TLS client certificates are not used for player identity. Player authentication
continues to use the application-specific Ed25519 challenge-response protocol.

### Server authentication modes

The socket policy supports two explicit modes:

- `PINNED_IDENTITY` disables DNS endpoint identification and requires a custom
  trust manager backed by ADR 0004's `ServerTrustService`;
- `PUBLIC_DNS` enables standard HTTPS-style endpoint identification and is used
  with an ordinary public-CA trust manager supplied by the caller.

For the first LAN/private implementation, the leaf TLS certificate public key is
Ed25519 and is also the server identity key. Its canonical SPKI derives the
`ServerId`. First use is read-only and aborts the handshake until the caller
explicitly confirms TOFU and reconnects. A changed identity and an expected-pin
mismatch always abort the handshake without mutating the trust store.

Using a separate long-term server identity key from the TLS certificate key
requires an exporter-bound server signature message and remains deferred. The
adapter must not claim that an unrelated public-CA certificate proves the
separate Sunderfront identity.

### Channel binding

Bouncy Castle 1.84 intentionally exposes TLS 1.3 exporter material only while it
invokes `notifyHandshakeComplete()`. The exporter master secret is cleared after
the callback. Reading `BCSSLConnection.getChannelBinding("tls-exporter")` later
from an otherwise completed socket fails and must not be worked around with a
sleep, retry loop, reflection, or retention of provider internals.

`Tls13Handshake` therefore registers a standard JSSE handshake-completed
listener before starting the handshake. Inside that callback it obtains the
BCJSSE connection and captures `tls-exporter`. Bouncy Castle maps this value to
the RFC 9266 TLS exporter channel binding. The captured result must be exactly 32
bytes and is immediately wrapped in ADR 0004's defensively copied, non-logging
`SecureChannelBinding` value.

The initial handshake itself is synchronous, but JSSE event delivery may occur
after `startHandshake()` returns. The adapter therefore waits on a
`CountDownLatch`, not a sleep or polling loop. The caller must supply a positive
completion timeout no longer than 30 seconds. Timeout or interruption closes the
channel and returns a bounded semantic error. The listener is removed after the
callback or terminal failure; no exporter secret or provider context is retained.

A missing callback, missing provider extension, incomplete handshake, null
exporter, wrong length, rejected TLS policy, or use of a non-BCJSSE socket fails
closed before the application creates or accepts an identity proof. A channel
whose exporter cannot be captured is closed before the error is returned.

### Certificate checks in pinned mode

Pinning validates possession of the certificate private key through the TLS
handshake and compares the canonical leaf public key to the configured trust
policy. The adapter also checks certificate validity, digital-signature key
usage, and TLS server-auth extended key usage when those extensions are present.
It does not silently turn a local pin into public-PKI validation or vice versa.

## Validation requirements

The module must include a real loopback TLS integration test that proves:

- TLS 1.3 and `sunderfront/1` are negotiated;
- both peers capture the same 32-byte `tls-exporter` value during their own
  handshake-completed callbacks;
- event delivery is awaited with an explicit bounded timeout;
- encrypted application data can make a bounded round trip;
- the inspected peer `ServerId` matches the certificate key;
- a different certificate key for an existing local reference aborts the
  handshake with the semantic `CHANGED_IDENTITY` cause preserved;
- first-use inspection does not persist trust automatically;
- an unsupported JSSE socket cannot masquerade as a channel-bound connection.

All dependency locks and SHA-256 verification metadata are committed. Bouncy
Castle upgrades require release-note and security-advisory review plus the full
quality gate.

## Consequences

- Java 21 clients and servers can use a standard TLS 1.3 implementation with the
  exact exporter required by the identity transcript.
- The exporter is captured only during the supported BCJSSE callback and then
  represented by an independent immutable 32-byte value.
- Callback delivery is synchronized explicitly and cannot block indefinitely.
- The first adapter is pure Java and does not add Netty, OpenSSL, BoringSSL, JNI,
  or platform-specific binaries.
- LAN/private servers can operate offline after explicit first-use confirmation.
- Public DNS validation remains available through a separately supplied standard
  trust manager.
- This slice does not yet implement protocol framing over the socket, dedicated
  server lifecycle integration, reconnect, realtime UDP/DTLS, or realtime
  session tokens. Issue #34 remains open after this PR.

## Rejected alternatives

- **Java 21 SunJSSE only:** TLS 1.3 is available, but the public exporter API
  required for RFC 9266 channel binding is not.
- **Post-handshake exporter polling:** BCJSSE clears the TLS 1.3 exporter secret
  after its completion callback; polling is racy and cannot restore erased key
  material.
- **Sleep before reading the callback result:** scheduler timing is not a
  synchronization contract and would make failures nondeterministic.
- **Certificate hash as channel binding:** binds a certificate rather than the
  exact secure connection and breaks legitimate certificate rotation.
- **Custom nonce/hash substitute:** not a standard exporter and does not provide
  the same channel-binding guarantee.
- **QUIC as the first dependency:** combines reliable transport, realtime design,
  native/runtime choices, and security policy before the simpler TLS boundary is
  proven.
- **Native OpenSSL/BoringSSL baseline:** increases packaging and platform risk for
  the first Windows/Linux/iGPU-compatible client slice.
- **Automatic TOFU persistence inside the trust manager:** hides the security
  decision and allows a first-connection attacker to become trusted silently.

## Upstream references

- Bouncy Castle Java 1.84 release notes and release announcement.
- Bouncy Castle `BCSSLConnection#getChannelBinding` implementation for
  `tls-exporter`.
- Bouncy Castle mock TLS peers exporting channel bindings from
  `notifyHandshakeComplete()`.
- Java 21 `SSLSocket` handshake and completion-event API.
- RFC 8446: TLS 1.3.
- RFC 9266: `tls-exporter` channel binding.
- RFC 7301: ALPN.
