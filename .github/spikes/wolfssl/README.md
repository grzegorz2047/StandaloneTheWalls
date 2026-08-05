# wolfSSL DTLS 1.3 provider spike

This directory contains non-production proof work for issue #167.

The spike builds exact upstream commits of native wolfSSL and wolfSSL JNI/JSSE on GitHub-hosted runners. It does not add wolfSSL to the Gradle dependency graph, release archives, runtime classpath or application license notices.

## Verified Linux proof

GitHub Actions builds the following exact upstream commits on Ubuntu 24.04 and Java 21:

- wolfSSL 5.9.1: `1d363f3adceba9d1478230ede476a37b0dcdef24`;
- wolfSSL JNI/JSSE 1.17.0: `2eebdb8db88c1d8f5531e92060a3a2741dd694de`.

The proof enables JNI, DTLS, DTLS 1.3 and PSK together, runs upstream `make check`, builds wolfSSL JNI/JSSE, records native dependencies and SHA-256 checksums, and then runs a real loopback against this repository's `OneTimeRealtimeTicketStore`.

The loopback verifies:

- dedicated DTLS 1.3 client and server methods;
- external-PSK callbacks with a 128-bit ticket identity and 256-bit PSK;
- the first handshake atomically returns store status `REDEEMED`;
- application data crosses the established DTLS transport;
- a second handshake with the same identity and PSK returns exactly `UNKNOWN_OR_REPLAYED` and cannot establish another session;
- temporary key copies are zeroed and public output is redacted;
- Java 21 datagram callback I/O works without reflection, `--add-opens` or a JNI fork.

Direct `WolfSSLSession.setFd(DatagramSocket)` is not usable on Java 21 because the pinned JNI code expects the removed private `DatagramSocket.impl` field. The proof therefore uses the provider's public per-session receive/send callback API with explicit socket contexts.

## Remaining blockers

This proof does not select wolfSSL and does not yet satisfy issue #165. The following remain unresolved:

- native wolfSSL exposes stateless DTLS accept, but wolfSSL JNI 1.17.0 does not expose a matching public Java method; `setGenCookie` alone operates after a `WolfSSLSession` exists and therefore does not yet prove bounded pre-session flood admission;
- the upstream Windows wolfSSL JNI workflow explicitly disables DTLS and DTLS 1.3 for its normal JNI build, so a separate pinned Windows x64 proof is required;
- Windows app-image relocation, native DLL discovery and release packaging remain unverified;
- provider licensing must be decided explicitly before any product dependency or binary distribution.

## License boundary

The project remains MIT. Upstream wolfSSL offers open-source and commercial licensing options. No wolfSSL binary or source is distributed by this spike, and no product dependency may be merged until the project owner records an explicit licensing decision.
