# Sunderfront v0.1.0-alpha.1 — Direct Connect Alpha

This is an **alpha** release for testing the first installable client/server
vertical slice. It is not a playable The Walls match yet.

## Included

- dedicated JVM server with reliable TLS 1.3 listener;
- local Ed25519 player identities and explicit fingerprint-based TOFU;
- Identity Proof V2 and policy admission;
- keyboard-first Direct Connect UI;
- minimal lobby membership and player snapshot;
- no-overwrite server credential generator;
- portable client and server ZIP archives requiring Java 21;
- SHA-256 checksums for every published archive.

## Known limitations

- no gameplay, map loading, teams, ready state, countdown, combat, or win logic;
- no realtime UDP/DTLS world transport;
- no automatic reconnect or session resume;
- no public server browser, relay, or NAT traversal;
- no production asset pack, final font, audio, or polished UI;
- no auto-update or signed platform installer;
- self-signed server certificates rely on explicit fingerprint comparison;
- protocol and local data formats may change incompatibly before beta.

Read the README inside each archive before first start. Verify `SHA256SUMS` before
unpacking or running downloaded files.
