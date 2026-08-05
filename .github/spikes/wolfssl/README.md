# wolfSSL DTLS 1.3 provider spike

This directory contains non-production proof work for issue #167. It evaluates pinned wolfSSL JNI/JSSE APIs without adding wolfSSL to Gradle, the product runtime, application images, release archives or license notices.

## Pinned upstreams

Both GitHub Actions proofs use Java 21 and exact upstream commits:

- wolfSSL 5.9.1: `1d363f3adceba9d1478230ede476a37b0dcdef24`;
- wolfSSL JNI/JSSE 1.17.0: `2eebdb8db88c1d8f5531e92060a3a2741dd694de`.

Linux builds native wolfSSL with JNI, DTLS, DTLS 1.3 and PSK enabled, runs upstream `make check`, builds wolfSSL JNI/JSSE and records the Java/native API surface, dependencies and checksums. Windows x64 builds the same pinned sources with DTLS 1.3 and PSK explicitly enabled instead of relying on the upstream workflow configuration that disables them.

## Positive capability proof

`PskDtls13Loopback.java` runs a real loopback against this repository's `OneTimeRealtimeTicketStore` and verifies:

- dedicated DTLS 1.3 client and server methods;
- external-PSK callbacks with a 128-bit ticket identity and 256-bit PSK;
- the first handshake atomically returns store status `REDEEMED`;
- application data crosses the established DTLS transport;
- a second handshake with the same identity and PSK returns exactly `UNKNOWN_OR_REPLAYED` and cannot establish another session;
- temporary key copies are zeroed and public output is redacted.

Direct `WolfSSLSession.setFd(DatagramSocket)` is not usable on Java 21 because the pinned JNI code expects the removed private `DatagramSocket.impl` field. The proof uses the provider's public per-session receive/send callbacks with explicit socket contexts, without reflection, `--add-opens` or a JNI fork.

## Negative matrix

`PskDtls13NegativeMatrix.java` exercises fail-closed behavior on Linux and from the relocated Windows bundle:

- an unknown identity fails with `UNKNOWN_OR_REPLAYED`;
- an expired identity fails with `EXPIRED` under an injected clock;
- a wrong client PSK causes the handshake to fail after the server has returned `REDEEMED`, and a retry with the correct PSK returns `UNKNOWN_OR_REPLAYED` rather than restoring the ticket;
- two simultaneous handshakes for one ticket produce exactly one successful `REDEEMED` session and one rejected `UNKNOWN_OR_REPLAYED` replay;
- a DTLS 1.2-only client cannot establish a session with the DTLS 1.3-only server;
- a blocked server accept can be interrupted, its executor terminates and the session/context cleanup path runs.

The workflows also launch the proof with a missing native provider and with a deliberately corrupt native library. Both processes must exit nonzero. Raw failure logs and provider binaries are not uploaded.

## Windows relocation and provenance findings

The Windows proof copies only the generated JAR, both DLLs and compiled proof classes into a clean directory whose path contains spaces. Runtime classpath and `java.library.path` point only at that relocated bundle. The proof records file sizes and SHA-256 values but uploads text reports only.

Repeated builds from the same source commits and runner image produced functionally equivalent green binaries with different SHA-256 values. The current build is therefore functionally repeatable, not byte-for-byte reproducible. Product adoption would require a defined SBOM/provenance policy or normalized build metadata rather than assuming stable binary hashes from source pins alone.

## Remaining blockers

This spike does not select wolfSSL and does not yet satisfy issue #165. The unresolved product blockers are:

- native wolfSSL exposes stateless DTLS accept, but wolfSSL JNI 1.17.0 has no matching public Java method; session-scoped `setGenCookie` starts after a `WolfSSLSession` exists and cannot prove bounded pre-session flood admission, cookie tamper rejection or source-address binding;
- the real Sunderfront application image and release archive remain intentionally unchanged, so provider discovery, missing/wrong-architecture behavior, SBOM and size impact still need verification in an approved product integration;
- byte-for-byte provenance and the version/CVE update process are unresolved;
- GPL/commercial licensing must be decided explicitly before any product dependency or binary distribution;
- the final ADR must either select an approved provider or record the decision to remain fail-closed while waiting for an acceptable upstream API.

## License boundary

The project remains MIT. Upstream wolfSSL offers open-source and commercial licensing options. No wolfSSL binary or source is distributed by this spike, and no product dependency may be merged until the project owner records an explicit licensing decision.
