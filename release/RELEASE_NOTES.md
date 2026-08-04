# Sunderfront v0.1.0-alpha.5 — Interactive Lobby Alpha

This is the **M2 Interactive Lobby Alpha** for testing the first continuous
client/server path from startup to a minimal 3D preparation scene. It is not yet
a complete The Walls match.

## What changed since alpha.4

- mouse and keyboard navigation from the start menu through Direct Connect and the
  connected lobby;
- a four-team lobby showing the authenticated player, all connected members, team
  occupancy, ready state, bounded server rejections, and authoritative phase state;
- server-authoritative team and ready commands with correlated results and complete
  canonical roster snapshots;
- a deterministic fixed-tick countdown that starts only when the complete lobby is
  valid, assigned across at least two teams, and ready;
- deterministic cancellation and restart when a player leaves, changes team, or
  becomes not ready before the final tick;
- exactly one transition into `PREPARATION`, followed by a verified minimal map and
  the authoritative spawn assigned for each player's team;
- first-person preparation controls with explicit cursor capture/release, horizontal
  WASD movement, yaw, bounded vertical pitch, point-to-world collision checks, and a
  bounded player-body radius;
- a packaged Windows app-image smoke that performs capture, movement, pitch, cursor
  release, relocation, and a second clean build;
- a licensed Andika-derived Unicode bitmap font covering current Polish and English
  UI text without relying on system fonts;
- deterministic font generation from the official GitHub release, with pinned tools,
  source SHA-256, PNG CRC validation, and final atlas SHA-256;
- portable JVM client/server ZIPs, a Java-free Windows x64 client ZIP, and one sorted
  `SHA256SUMS` covering every published archive.

## M2 test procedure

The default lobby requires at least two ready players in at least two represented
teams.

1. Extract `sunderfront-server-0.1.0-alpha.5.zip` into a new writable directory.
2. On Windows, run `1_GENERUJ_CREDENTIALS.bat`, keep the private files local, then
   start the server with `2_URUCHOM_SERWER.bat`.
3. Extract two separate copies of
   `sunderfront-client-windows-x64-0.1.0-alpha.5.zip` and run `Sunderfront.exe` in
   each copy. The separate directories intentionally create separate player
   identities.
4. Connect both clients to the server's explicit `host:port`, compare the displayed
   fingerprint with `credentials/server-fingerprint.txt`, and trust it only after
   the value matches.
5. In the lobby, place the two players in different teams and set both to ready.
   Both clients must display the same authoritative countdown.
6. Before zero, make one player not ready. The countdown must cancel on both
   clients. Set that player ready again and verify that a fresh full countdown
   starts.
7. Let the countdown reach zero. Each client must enter `PREPARATION` exactly once,
   load the verified minimal scene, and appear at the authoritative spawn for its
   team without restarting the client.
8. Click the scene or press Enter to capture the cursor. Verify WASD movement,
   horizontal and vertical mouse look, the pitch limit, collision with scene
   geometry, and Esc cursor release. Re-capture once to prove the transition is
   repeatable.
9. Close both clients and stop the server. No credential, identity, trust, cache, or
   runtime-data file should be present in the downloaded archives themselves.

The JVM client can be used instead of the Windows app image when Java 21 is already
installed. Use two independent extracted directories so the clients do not share a
portable `data` directory.

## Server upgrade from an older alpha

1. Extract `sunderfront-server-0.1.0-alpha.5.zip` into a new empty directory.
2. Do not copy only the numbered `.bat` files into an older `bin/lib` tree.
3. Back up the old `credentials` and `data` directories.
4. When the old credential directory contains all four non-empty files, copy the
   entire directory as one identity set into the alpha.5 package.
5. Run `1_GENERUJ_CREDENTIALS.bat`; a complete set is accepted without modifying
   any file or hash.
6. Run `2_URUCHOM_SERWER.bat`.

Never combine individual credential files from different generator runs and never
publish the private key or server data directory.

## Known limitations

- preparation currently provides only movement, camera control, collision, a minimal
  verified map, and team spawns;
- no resource gathering, mining, building, crafting, classes, inventory, equipment,
  wall opening, combat, deathmatch, results screen, or next-round reset;
- no authoritative realtime movement replication between players yet;
- no automatic reconnect or session resume;
- no public server browser, relay, NAT traversal, or CGNAT workaround;
- no production art pack, character models, animation, audio, or final UI styling;
- no MSI installer, auto-update, or Authenticode/platform signature;
- Windows may warn about the unfamiliar unsigned executable;
- the dedicated server and technical JVM client still require 64-bit Java 21;
- self-signed server certificates rely on explicit fingerprint comparison;
- protocol, maps, and local data formats may change incompatibly before beta.

Windows players should normally download
`sunderfront-client-windows-x64-0.1.0-alpha.5.zip`. Users with an existing Java 21
runtime can use the technical JVM archive
`sunderfront-client-0.1.0-alpha.5.zip`. Server operators should use
`sunderfront-server-0.1.0-alpha.5.zip`. Download all three archives with
`SHA256SUMS` when auditing the release, and verify the hashes before unpacking or
running any file.
