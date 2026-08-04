# Sunderfront JVM client 0.1.0-alpha.5 — Interactive Lobby Alpha

This is the technical cross-platform JVM distribution for the M2 path from the
start menu through an authoritative lobby into a minimal 3D `PREPARATION` scene.
It requires a separately installed 64-bit Java 21 runtime. Windows x64 users
should normally download
`sunderfront-client-windows-x64-0.1.0-alpha.5.zip` instead; that package includes
its own restricted runtime and starts through `Sunderfront.exe`.

## Start this JVM archive on Windows

Extract the complete archive, install 64-bit Java 21, then double-click:

```text
URUCHOM_KLIENTA.bat
```

The root launcher checks Java, keeps the console open on failure, and starts the
production client with the portable `data` directory. `README-PL.txt` contains
the Polish M2 guide.

## Technical start on Linux or Windows

Linux and compatible shells can use the generated JVM launcher directly:

```bash
bin/sunderfront-client --data-dir data
```

The equivalent Windows command is:

```powershell
bin\sunderfront-client.bat --data-dir data
```

`data` contains the local Ed25519 player identity and trusted-server store. Back
it up if you want to keep the same player identity. Do not share its contents and
do not point two simultaneous test clients at the same `data` directory.

Choose **Play**, enter an explicit `host:port` such as `127.0.0.1:27420`, and use
a handle containing 3-24 lowercase letters, digits, or underscores. The first
connection displays the server fingerprint and stops. Compare it with the server
operator before choosing **Trust and reconnect**. A later identity change is
blocked and cannot be accepted from the warning screen.

The UI is controllable by mouse and keyboard. The connected lobby displays four
teams, all players, team occupancy, ready state, server rejections, and the
authoritative match phase.

## M2 lobby and preparation test

The default server requires two ready players in at least two represented teams.
Use two separately extracted client directories so they have different identities.

1. Connect both clients to the same server and verify its fingerprint.
2. Put the clients in different teams and set both to ready.
3. Confirm that both clients display the same countdown.
4. Make one client not ready before zero; both clients must return to waiting.
5. Set it ready again and let the fresh countdown finish.
6. Both clients must enter `PREPARATION` once, without restarting, and load the
   verified minimal scene at their authoritative team spawns.
7. Click the scene or press Enter to capture the cursor. Use WASD to move and the
   mouse to look horizontally and vertically. Pitch is bounded and scene collision
   includes a bounded player-body radius.
8. Press Esc to release the cursor, then capture it again once.

The current preparation scene is intentionally minimal. It proves map verification,
spawn ownership, camera/movement lifecycle, and collision; it is not a complete
match.

The included `assets/assets.lock.json` is intentionally valid and empty. The
minimal preparation map is project-owned and packaged through the verified map
bundle, while the licensed Unicode UI font is generated reproducibly during CI and
included in the application resources.

## Archive layout

- `URUCHOM_KLIENTA.bat` is the convenience entry point for this JVM archive;
- `bin/` contains generated technical JVM launchers;
- `lib/` contains runtime libraries, not a source checkout;
- `tools/` contains release diagnostics, not the normal game entry point;
- `assets/` contains the pinned asset lock;
- `data/` is created locally and contains private player state.

## Distribution smoke tools

The launchers under `tools/` are intended for automated release verification. The
release pipeline uses the production Direct Connect path and the packaged
preparation smoke to verify startup, cursor capture, movement, pitch, release,
relocation, and a second clean Windows app-image build.

## Known limitations

This alpha does not include resource gathering, mining, building, crafting,
classes, inventory, equipment, wall opening, combat, deathmatch, results, or a
next-round reset. Player movement is not yet replicated as an authoritative
realtime world snapshot. There is no reconnect, public server browser, relay, NAT
traversal, production art pack, character animation, audio, updater, installer, or
platform signature. This JVM archive does not bundle Java; the separate Windows
x64 archive does.
