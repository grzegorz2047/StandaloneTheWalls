# Sunderfront visual playtest

The shortest working path uses the published
[`v0.1.0-alpha.5`](https://github.com/grzegorz2047/StandaloneTheWalls/releases/tag/v0.1.0-alpha.5)
release and does not require building the game from source.

This alpha reuses the existing project stack instead of parallel implementations:

- jMonkeyEngine 3.9.0-stable for the 3D scene;
- the Gradle Application Plugin for client and server distributions;
- Bouncy Castle TLS 1.3 for secure Direct Connect;
- a versioned and verified `.twmap` as the world source;
- the authoritative server roster, ready state, countdown, and spawn assignment.

## Downloads

Download these assets from release `v0.1.0-alpha.5`:

1. `sunderfront-server-0.1.0-alpha.5.zip`;
2. `sunderfront-client-windows-x64-0.1.0-alpha.5.zip`.

The Windows x64 client includes `Sunderfront.exe` and a limited Java 21 runtime.
The server requires a separately installed 64-bit Java 21 runtime.

## Local launch

### 1. Server

1. Extract the complete server archive into a new directory.
2. Run `1_GENERUJ_CREDENTIALS.bat`.
3. Open `credentials/server-fingerprint.txt` and keep the public fingerprint.
4. Run `2_URUCHOM_SERWER.bat`.

Never share `credentials/server-ed25519-key.pk8` or the server `data` directory.

### 2. Two clients

The default configuration requires two ready players in at least two teams.

1. Extract the client archive twice into separate directories, for example
   `client-a` and `client-b`.
2. Run `Sunderfront.exe` from both directories.
3. Choose `Play` and enter `127.0.0.1:27420`.
4. Use two different handles, for example `demo_a` and `demo_b`.
5. On first connection, compare the client fingerprint with
   `server-fingerprint.txt`, accept trust, and reconnect.
6. Choose two different teams and mark both players ready.
7. Let the authoritative countdown reach zero.

Both clients should enter the 3D `PREPARATION` scene exactly once without a
process restart and appear at server-assigned team spawns.

## World controls

- click the scene or press `Enter` to capture the pointer;
- use `WASD` for local movement;
- move the mouse for horizontal and vertical camera rotation;
- press `Esc` to release the pointer;
- press `Esc` again to return to the menu through controlled disconnect.

Movement remains inside the verified team region and is checked against a
separate invisible collision graph.

## What is generated and verified

The minimal world is not stored as an opaque binary asset in ordinary Git
history. The project deterministically generates the complete
`minimal_preparation` bundle from readable sources and verifies:

- the manifest and complete archive SHA-256;
- the visual scene GLB and separate collision GLB;
- four team regions, perimeter and central walls, ground, and lighting;
- 40 unique spawns, ten per team;
- server and client agreement before entering the scene.

The server chooses the spawn from the authoritative team. The client does not
invent a default position and refuses to enter the world when the bundle, hash,
version, or assignment is invalid.

## Current limitations

This is a visual vertical slice, not a complete match. Mining, building,
crafting, classes, inventory, wall opening, combat, results, and the next round
are not implemented. Movement shown in `PREPARATION` is currently client-local
and is not yet replicated through authoritative realtime snapshots.

Realtime remains fail-closed. The alpha uses working reliable TLS 1.3 for
connection, lobby, and world entry; it does not silently downgrade to DTLS 1.2
or ship an unapproved native provider.
