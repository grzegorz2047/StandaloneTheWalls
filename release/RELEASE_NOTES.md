# Sunderfront v0.1.0-alpha.2 — Windows First-Run Alpha

This is an **alpha** release for testing the first client/server vertical slice.
It is not a playable The Walls match yet.

## First-run improvements since alpha.1

- obvious root `URUCHOM_KLIENTA.bat` entry point for the Windows client;
- numbered `1_GENERUJ_CREDENTIALS.bat` and `2_URUCHOM_SERWER.bat` server flow;
- the normal server launcher always supplies server, identity, and TLS
  configuration, preventing a misleading network-disabled process;
- actionable checks for a 64-bit Java 21 runtime and a console pause after
  launcher failures;
- Polish `README-PL.txt` guides in both archives;
- release-only Direct Connect smoke launchers moved from `bin/` to `tools/`;
- Windows CI executes the root client launcher, credential generator, and full
  server configuration validation from fresh installed distributions.

## Included

- dedicated JVM server with reliable TLS 1.3 listener;
- local Ed25519 player identities and explicit fingerprint-based TOFU;
- Identity Proof V2 and policy admission;
- keyboard-first Direct Connect UI;
- minimal lobby membership and player snapshot;
- no-overwrite server credential generator;
- portable client and server ZIP archives requiring 64-bit Java 21;
- SHA-256 checksums for every published archive.

## Known limitations

- no gameplay, map loading, teams, ready state, countdown, combat, or win logic;
- no realtime UDP/DTLS world transport;
- no automatic reconnect or session resume;
- no public server browser, relay, or NAT traversal;
- no production asset pack, final font, audio, or polished UI;
- no bundled Java runtime, `.exe`, auto-update, or signed platform installer;
- self-signed server certificates rely on explicit fingerprint comparison;
- protocol and local data formats may change incompatibly before beta.

Read `README-PL.txt` or `README.md` inside each archive before first start. Verify
`SHA256SUMS` before unpacking or running downloaded files.
