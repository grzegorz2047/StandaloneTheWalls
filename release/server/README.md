# Sunderfront dedicated server 0.1.0-alpha.5 — Interactive Lobby Alpha

This M2 dedicated server provides secure Direct Connect, an authoritative
four-team lobby, ready-state countdown, and the transition into a minimal 3D
`PREPARATION` scene with team-owned spawns. It remains a portable JVM
distribution and requires a separately installed 64-bit Java 21 runtime. No
private key, certificate, identity database, trust store, registry cache, or
runtime player data is included in the archive. The Java-free `Sunderfront.exe`
package applies only to the Windows client.

## Windows first run

Extract the complete archive into a new writable directory and use the numbered
root launchers:

1. double-click `1_GENERUJ_CREDENTIALS.bat`;
2. double-click `2_URUCHOM_SERWER.bat` for every server start.

Do not copy only the numbered launchers into an older package. The root launchers,
`bin/`, `lib/`, `config/`, and `tools/` must come from the same complete archive.
The credential launcher rejects a missing technical launcher or Java-check helper
as an incomplete or mixed-version package.

The first launcher is safe to run again. If all four credential files already
exist and are non-empty, it preserves the current server identity, prints the
existing public fingerprint, and exits successfully without invoking the
generator. A partial or empty-file set fails closed without creating, deleting,
or replacing any credential file.

Both launchers check for a 64-bit Java 21 runtime when Java execution is needed
and keep the console open after an error. The second launcher refuses to start
without the required credentials and always supplies the server, identity, and
TLS configuration files. This is the normal Windows path that opens the reliable
TLS listener and authoritative lobby.

`README-PL.txt` contains the Polish operator guide and the recovery procedure for
a partial credential directory.

## M2 authoritative lobby test

The standard lobby accepts up to 40 players, up to 10 per team, and requires a
minimum of two ready players in at least two represented teams before starting.

1. Start the server and give players only the public value from
   `credentials/server-fingerprint.txt`.
2. Connect two clients using different identities and assign them to different
   teams.
3. Set both clients ready. The server must publish one fixed-tick countdown and
   both clients must display the same value.
4. Before zero, make one player not ready or change team. The server must cancel
   the countdown for every client. Restoring a complete ready lobby starts a new
   full countdown.
5. At zero, the server must enter `PREPARATION` exactly once, reject further team
   and ready mutations, load the verified minimal map, and provide the correct
   authoritative spawn for every admitted player.
6. Client movement in the preparation scene is currently local presentation. The
   server does not yet publish realtime world movement snapshots.

## Upgrade from an older alpha

Extract `sunderfront-server-0.1.0-alpha.5.zip` into a new empty directory. Back up
both `credentials/` and `data/`. When the old credential directory contains all
four non-empty files, copy the entire directory as one set into the alpha.5
package. Do not combine individual files from different generator runs. Running
the root credential launcher over a complete set is idempotent and does not
change its hashes.

## Technical credential generation

Linux and compatible shells can run:

```bash
bin/sunderfront-server-credentials --output credentials
```

The equivalent Windows technical command is:

```powershell
bin\sunderfront-server-credentials.bat --output credentials
```

The technical generator refuses to overwrite any target. It creates an Ed25519
PKCS#8 private key, a self-signed X.509 server certificate, a public registry-root
file suitable for the alpha `LOCAL_TOFU` configuration, and
`server-fingerprint.txt`. Protect the private key and back it up together with
`data/identity.sqlite`. Share only the fingerprint with players.

Never combine files from different generator runs. The private key, certificate,
and fingerprint form one identity set. Before recovering from a partial set,
back up the entire `credentials` directory. Creating a fresh empty set changes
the server identity and causes returning clients to warn about a changed
fingerprint.

## Validate configuration

The Windows root launcher forwards additional arguments, so validation is:

```powershell
.\2_URUCHOM_SERWER.bat --validate-config
```

The equivalent technical command is:

```bash
bin/sunderfront-server \
  --config config/server.properties \
  --identity-config config/identity.properties \
  --tls-config config/tls.properties \
  --validate-config
```

## Technical server start

Linux and automation can run:

```bash
bin/sunderfront-server \
  --config config/server.properties \
  --identity-config config/identity.properties \
  --tls-config config/tls.properties
```

Starting only `bin/sunderfront-server` or its `.bat` without these arguments is a
network-disabled technical mode. It logs that local identity, reliable TLS, and
the lobby are disabled and cannot accept the Direct Connect client.

The reliable TLS listener uses TCP port `27420` by default. Open or forward that
port only when remote players should connect. LAN players can use the server's
LAN address. This alpha does not provide relay, NAT traversal, or discovery.

## Archive layout

- `1_GENERUJ_CREDENTIALS.bat` and `2_URUCHOM_SERWER.bat` are the normal Windows
  entry points;
- `bin/` contains generated technical JVM launchers;
- `lib/` contains runtime libraries, not a source checkout;
- `config/` contains non-secret process configuration;
- `credentials/` receives locally generated key material;
- `data/` receives private runtime state;
- `tools/` contains internal package helpers.

## Files created at runtime

- `credentials/server-ed25519-key.pk8` — secret server private key;
- `credentials/server-ed25519-certificate.der` — public certificate;
- `credentials/registry-trust-roots.hex` — public alpha registry root;
- `credentials/server-fingerprint.txt` — public fingerprint to compare;
- `data/identity.sqlite` — local handle bindings and bans;
- `data/registry.sfrb` — optional verified registry cache.

Never publish the private key, SQLite database, or runtime data directory.

## Known limitations

The server stops the implemented match lifecycle in minimal `PREPARATION`. It has
no resource gathering, building, crafting, classes, inventory, wall opening,
combat, deathmatch, results, next-round reset, authoritative realtime movement,
reconnect, public server browser, relay, NAT traversal, remote administration,
production art/audio, automatic certificate rotation, bundled server runtime,
server executable, or signed installer. The self-signed certificate is
authenticated by explicit fingerprint pinning, not public PKI.
