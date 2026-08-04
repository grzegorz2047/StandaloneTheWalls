# Sunderfront Windows x64 client 0.1.0-alpha.5 — Interactive Lobby Alpha

This M2 alpha provides the continuous path from the start menu through secure
Direct Connect and an authoritative four-team lobby into a minimal 3D
`PREPARATION` scene. It is not yet a complete The Walls match.

## Start

1. Extract the complete archive to a writable directory.
2. Run `Sunderfront.exe`.
3. Do not install Java separately. The required restricted 64-bit Java 21 runtime
   is included in `runtime/`.
4. Choose **Play**, enter an explicit `host:port`, and use a canonical handle.
5. On first use, compare the displayed server fingerprint with the operator before
   choosing **Trust and reconnect**.

The launcher keeps the private player identity and trusted-server records in a
portable `data/` directory beside `Sunderfront.exe`. Do not share it. Move or back
up the entire application directory rather than copying only the executable. Use
separate extracted directories for simultaneous test clients.

Server operators should use the complete
`sunderfront-server-0.1.0-alpha.5.zip` archive. Do not copy only numbered server
launchers into an older `bin/lib` package.

## M2 test

The default server needs two ready players in at least two represented teams.

1. Start two clients from separate extracted directories and connect them to the
   same verified server.
2. Select different teams and set both players to ready.
3. Confirm that both clients display the same countdown.
4. Cancel it once by making one player not ready, then start a fresh countdown.
5. At zero, both clients must enter `PREPARATION` exactly once and load the
   verified scene at their authoritative team spawns.
6. Click the scene or press Enter to capture the cursor. Verify WASD movement,
   yaw, bounded pitch, scene collision, Esc release, and one re-capture.

## Contents

- `Sunderfront.exe` — normal Windows entry point;
- `runtime/` — restricted Java 21 runtime used only by this application;
- `app/` — application libraries and launcher configuration;
- `assets/` — pinned asset lock;
- `README.md` and `README-PL.txt` — instructions and M2 test procedure;
- `ICON-LICENSE.md` — icon provenance.

## Known limitations

This unsigned alpha has no resource gathering, building, crafting, classes,
inventory, wall opening, combat, deathmatch, results, next-round reset, realtime
movement replication, reconnect, server browser, relay, NAT traversal, final art,
character animation, audio, installer, Authenticode signature, or auto-update.
Windows may warn about an unfamiliar unsigned executable. Download only from the
project Release and verify `SHA256SUMS`.
