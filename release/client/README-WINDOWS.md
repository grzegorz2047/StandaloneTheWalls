# Sunderfront Windows x64 client 0.1.0-alpha.3

This is a technical alpha for secure Direct Connect and minimal lobby entry. It
is not a playable The Walls match yet.

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
up the entire application directory rather than copying only the executable.

## Contents

- `Sunderfront.exe` — normal Windows entry point;
- `runtime/` — restricted Java 21 runtime used only by this application;
- `app/` — application libraries and launcher configuration;
- `assets/` — pinned asset lock;
- `README.md` and `README-PL.txt` — instructions;
- `ICON-LICENSE.md` — icon provenance.

## Known limitations

This unsigned alpha has no installer, Authenticode signature, auto-update,
gameplay, final map, teams, combat, audio, or production assets. Windows may warn
about an unfamiliar unsigned executable. Download only from the project release
and verify `SHA256SUMS`.
