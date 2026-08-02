# ADR 0005: Low-level Bouncy Castle TLS 1.3 reliable transport

- Status: Accepted for the first reliable-transport slice
- Date: 2026-08-02
- Issues: #34
- Depends on: ADR 0002, ADR 0003, ADR 0004

## Context

Sunderfront needs a secure, reliable connection before application-level player
identity messages are exchanged. The player proof defined by ADR 0003 and ADR
0004 must include the RFC 9266 `tls-exporter` binding for the exact encrypted
connection.

Java 21's standard JSSE API can negotiate TLS 1.3 but does not expose a public
TLS exporter API. Bouncy Castle 1.84 provides both a JSSE provider and a public
low-level TLS API. Investigation and real loopback tests showed that BCJSSE is
not suitable for this binding requirement: the TLS 1.3 exporter secret is
available inside Bouncy Castle's internal peer `notifyHandshakeComplete()`
callback and is cleared after that callback, while the standard JSSE
`HandshakeCompletedListener` is dispatched later on another thread.

The implementation must not replace the exporter with a certificate hash,
nonce, socket address, TLS session identifier, sleep, polling loop, reflection,
or retained provider internals.

## Decision

The first reliable-transport adapter uses the public, low-level Bouncy Castle
TLS API from Bouncy Castle Java 1.84:

- `bcprov-jdk18on` for cryptographic primitives;
- `bctls-jdk18on` for `TlsClientProtocol`, `TlsServerProtocol`, TLS peer APIs,
  certificate handling, and exporter access;
- `bcpkix-jdk18on` only in tests to generate short-lived Ed25519 certificate
  fixtures.

The adapter lives in the renderer-independent `transport-bctls` module. Core
protocol and game-domain modules remain independent of concrete sockets and
Bouncy Castle.

### Cryptography isolation

Each client or server handshake creates `JcaTlsCrypto` through
`JcaTlsCryptoProvider` with an explicitly constructed `BouncyCastleProvider`.
The adapter does not call `Security.addProvider`, does not modify JVM security
properties, and does not rely on global provider ordering.

### Socket ownership and limits

`Tls13ClientConnector` and `Tls13ServerAcceptor` take ownership of an already
connected blocking `Socket`. Before TLS reads begin, the socket must be open,
connected, and configured with a read timeout from 1 through 30000 milliseconds.
A missing or excessive timeout fails before the handshake.

A successful connector returns `Tls13Connection`, which owns the low-level TLS
protocol and the underlying socket. Closing the connection attempts to close
both and preserves secondary close failures as suppressed exceptions. A failed
handshake closes the socket before propagating the failure.

Connection establishment, DNS resolution, listener ownership, admission limits,
and thread-pool policy belong to later client/server runtime adapters.

### Negotiation policy

The adapter permits only:

- TLS 1.3;
- ALPN `sunderfront/1`;
- `TLS_AES_128_GCM_SHA256`;
- `TLS_CHACHA20_POLY1305_SHA256`;
- `TLS_AES_256_GCM_SHA384`.

TLS 1.2 and earlier, a missing or changed ALPN value, and any cipher outside the
allowlist fail the handshake. TLS client certificates are not requested because
player identity continues to use the application-specific Ed25519 proof.

### Server identity and pinned trust

The first slice supports the pinned/TOFU trust model required for LAN and private
servers. The TLS leaf certificate must contain an Ed25519 public key. Its
canonical SubjectPublicKeyInfo derives the existing `ServerId`; the corresponding
private key signs the TLS handshake.

The low-level client converts the bounded X.509 chain to JCA certificates and
passes it to `PinnedServerTrustManager`. The trust manager checks validity,
digital-signature key usage, TLS server-auth extended key usage when present,
and the ADR 0004 trust policy.

First use remains read-only and aborts the connection until the caller explicitly
confirms TOFU and reconnects. A changed identity or expected-pin mismatch aborts
the handshake without mutating the trust store. The semantic `TlsTrustException`
remains in the exception cause chain for the UI or server browser to interpret.

Public-CA/DNS validation is intentionally not mixed into this adapter. It needs a
separate trust implementation and integration tests for hostname validation,
chain building, revocation policy, and certificate rotation.

### Channel binding lifecycle

Both low-level peers override their synchronous `notifyHandshakeComplete()`
callback. Inside that callback, before Bouncy Castle clears the exporter master
secret, the adapter:

1. verifies TLS 1.3;
2. verifies the negotiated cipher suite;
3. verifies ALPN `sunderfront/1`;
4. obtains `context.exportChannelBinding(ChannelBinding.tls_exporter)`;
5. requires exactly 32 bytes;
6. immediately copies the value into the non-logging `SecureChannelBinding`;
7. stores only immutable public session metadata.

No exporter secret, TLS context, certificate bytes, or provider-internal object
is retained after the callback. The client metadata also includes the verified
`ServerId`; the server metadata includes its configured credential identity.

## Validation requirements

The module must include a real loopback integration test over ordinary TCP
sockets that proves:

- client and server complete a TLS 1.3 handshake;
- ALPN `sunderfront/1` and an allowed cipher are negotiated;
- both peers capture the same 32-byte `tls-exporter` value inside their low-level
  completion callbacks;
- the client observes the certificate-derived `ServerId`;
- encrypted application data makes a bounded round trip;
- a different certificate key for an existing reference aborts the handshake
  with semantic `CHANGED_IDENTITY` preserved;
- the trust store is not rewritten by the failed handshake;
- unconnected sockets and sockets without a bounded read timeout fail before TLS
  I/O.

Unit tests cover isolated cryptography creation, first-use read-only behavior,
explicit confirmation, expected-pin mismatch, changed identity, certificate
usage checks, bounded error metadata, and server credential validation.

All dependency locks and SHA-256 verification metadata are committed. Bouncy
Castle upgrades require release-note and security-advisory review plus the full
quality gate.

## Consequences

- Java 21 clients and servers can use standard TLS 1.3 with the exact exporter
  required by the player identity transcript.
- Exporter capture follows the lifecycle supported by Bouncy Castle instead of
  depending on delayed JSSE events.
- The adapter is pure Java and adds no Netty, OpenSSL, BoringSSL, JNI, native
  binary, paid service, or central certificate authority.
- LAN/private servers can operate offline after explicit first-use confirmation.
- Each connection currently uses a blocking socket and must run outside the
  simulation thread.
- This slice does not yet implement protocol framing, runtime listener/connector
  services, persistent trust stores, production certificate provisioning,
  reconnect, public PKI, realtime DTLS/UDP, or realtime session tokens. Issue
  #34 remains open after this PR.

## Rejected alternatives

- **Java 21 SunJSSE:** TLS 1.3 is available, but the required public exporter API
  is not.
- **BCJSSE plus `HandshakeCompletedListener`:** Bouncy Castle clears the TLS 1.3
  exporter secret after the internal peer callback, while the JSSE event is
  dispatched later on another thread.
- **Sleep, polling, or callback latches around BCJSSE:** synchronization cannot
  restore key material that was already destroyed.
- **Certificate hash as channel binding:** it identifies a certificate rather
  than the exact TLS connection and harms ordinary certificate rotation.
- **Custom nonce/hash substitute:** it is not the RFC 9266 exporter and does not
  provide equivalent channel binding.
- **QUIC as the first adapter:** it combines reliable transport, realtime design,
  native/runtime decisions, and security policy before the simpler TLS boundary
  is proven.
- **Native OpenSSL/BoringSSL baseline:** it increases packaging and platform risk
  for the first Windows/Linux client and dedicated server.
- **Automatic TOFU persistence during certificate validation:** it silently
  trusts a possible first-connection attacker.

## Upstream references

- Bouncy Castle Java 1.84 release notes and release announcement.
- Bouncy Castle low-level `TlsClientProtocol` and `TlsServerProtocol` APIs.
- Bouncy Castle low-level peer `notifyHandshakeComplete()` examples and exporter
  handling.
- RFC 8446: TLS 1.3.
- RFC 9266: `tls-exporter` channel binding.
- RFC 7301: ALPN.
