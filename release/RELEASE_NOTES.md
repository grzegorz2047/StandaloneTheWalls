# Sunderfront v0.1.0-alpha.4 — Windows First-Run Reliability Alpha

This is an **alpha** release for testing the first client/server vertical slice.
It is not a playable The Walls match yet.

## Server credential improvements since alpha.3

- `1_GENERUJ_CREDENTIALS.bat` is now idempotent for an existing complete
  credential set: it preserves every file, prints the existing public fingerprint,
  and directs the operator to `2_URUCHOM_SERWER.bat`;
- a partial set, an empty required file, or a directory in place of a credential
  fails closed without creating, deleting, repairing, or overwriting anything;
- the launcher lists the missing or invalid paths and tells the operator to back up
  the entire `credentials` directory before recovery;
- a numbered launcher copied into an older or incomplete server package is rejected
  before Java starts, with instructions to extract the complete server ZIP into a
  new empty directory;
- Windows CI proves by SHA-256 that a repeated launch does not change any of the
  four identity files and that partial or mixed-package failures preserve existing
  bytes;
- the Polish and English server guides document safe migration of the whole
  credential set and warn that generating a fresh set changes the server identity.

The server still requires a separately installed 64-bit Java 21 runtime. Windows
x64 players should normally use the Java-free client archive containing
`Sunderfront.exe`.

## Included

- dedicated JVM server with reliable TLS 1.3 listener;
- local Ed25519 player identities and explicit fingerprint-based TOFU;
- Identity Proof V2 and policy admission;
- keyboard-first Direct Connect UI;
- minimal lobby membership and player snapshot;
- fail-closed, no-overwrite server credential generation;
- Java-free Windows x64 client app image with a restricted Java 21 runtime;
- portable JVM client and server ZIP archives;
- one `SHA256SUMS` covering every published archive.

## Server upgrade from an older alpha

1. Extract `sunderfront-server-0.1.0-alpha.4.zip` into a new empty directory.
2. Do not copy only the numbered `.bat` files into an older `bin/lib` tree.
3. When the old server has all four non-empty credential files, copy the entire
   `credentials` directory as one set into the alpha.4 directory.
4. Keep a backup of `credentials` and `data` before starting the new package.
5. Run `1_GENERUJ_CREDENTIALS.bat`; a complete set is accepted without changes.
6. Run `2_URUCHOM_SERWER.bat`.

Never combine files from different generator runs and never publish the private
key or the server data directory.

## Known limitations

- no gameplay, map loading, teams, ready state, countdown, combat, or win logic;
- no realtime UDP/DTLS world transport;
- no automatic reconnect or session resume;
- no public server browser, relay, or NAT traversal;
- no production asset pack, final font, audio, or polished UI;
- no MSI installer, auto-update, or Authenticode/platform signature;
- Windows may warn about the unfamiliar unsigned executable;
- the dedicated server and technical JVM client still require Java 21;
- self-signed server certificates rely on explicit fingerprint comparison;
- protocol and local data formats may change incompatibly before beta.

Windows players should download
`sunderfront-client-windows-x64-0.1.0-alpha.4.zip`. Server operators should use
`sunderfront-server-0.1.0-alpha.4.zip`. Verify `SHA256SUMS` before unpacking or
running downloaded files.
