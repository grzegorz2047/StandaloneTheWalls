# Sunderfront client 0.1.0-alpha.2

This is the second Direct Connect Alpha. It requires a 64-bit Java 21 runtime.
No installer or bundled JRE is included.

## Start on Windows

Extract the complete archive, then double-click:

```text
URUCHOM_KLIENTA.bat
```

The root launcher checks for a 64-bit Java 21 runtime, keeps the console open on
failure, and starts the production client with the portable `data` directory.
`README-PL.txt` contains the short Polish first-run guide.

## Technical start on Linux or Windows

Linux and other compatible shells can use the generated JVM launcher directly:

```bash
bin/sunderfront-client --data-dir data
```

The equivalent Windows command is:

```powershell
bin\sunderfront-client.bat --data-dir data
```

`data` contains the local Ed25519 player identity and trusted-server store. Back
it up if you want to keep the same player identity. Do not share its contents.

Choose **Play**, enter an explicit `host:port` such as `127.0.0.1:27420`, and use
a handle containing 3-24 lowercase letters, digits, or underscores. The first
connection displays the server fingerprint and stops. Compare it with the server
operator before choosing **Trust and reconnect**. A later identity change is
blocked and cannot be accepted from the warning screen.

The included `assets/assets.lock.json` is intentionally valid and empty. This
alpha does not require a production asset pack.

## Archive layout

- `URUCHOM_KLIENTA.bat` is the normal Windows entry point;
- `bin/` contains generated technical JVM launchers;
- `lib/` contains runtime libraries, not a source checkout;
- `tools/` contains release diagnostics, not the normal game entry point;
- `data/` is created locally and contains private player state.

## Distribution smoke tool

`tools/sunderfront-direct-connect-smoke` and the matching `.bat` file are intended
for release verification. They use the same production Direct Connect service as
the UI and require an explicit expected fingerprint.

## Known limitations

This alpha proves secure Direct Connect and minimal lobby membership. It has no
gameplay, realtime world transport, reconnect, public server browser, team
selection, maps, final assets, audio, updater, bundled Java runtime, executable,
or platform-signed installer. The current bitmap font also limits Polish UI text
to ASCII.
