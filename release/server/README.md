# Sunderfront dedicated server 0.1.0-alpha.2

This is the second Direct Connect Alpha dedicated server. It requires a 64-bit
Java 21 runtime. No private key, certificate, identity database, trust store, or
registry cache is included in this archive.

## Windows first run

Extract the complete archive and use the numbered root launchers:

1. double-click `1_GENERUJ_CREDENTIALS.bat` exactly once;
2. double-click `2_URUCHOM_SERWER.bat` for every server start.

Both launchers check for a 64-bit Java 21 runtime and keep the console open after
an error. The second launcher refuses to start without the required credentials
and always supplies the server, identity, and TLS configuration files. This is
the normal Windows path that opens the reliable TLS listener and minimal lobby.

`README-PL.txt` contains the short Polish operator guide.

## Technical credential generation

Linux and other compatible shells can run:

```bash
bin/sunderfront-server-credentials --output credentials
```

The equivalent Windows technical command is:

```powershell
bin\sunderfront-server-credentials.bat --output credentials
```

The generator refuses to overwrite any target. It creates an Ed25519 PKCS#8
private key, a self-signed X.509 server certificate, a public registry-root file
suitable for the alpha `LOCAL_TOFU` configuration, and `server-fingerprint.txt`.
Protect the private key and back it up together with `data/identity.sqlite`.
Share only the fingerprint with players.

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
minimal lobby are disabled and cannot accept the Direct Connect client.

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

This alpha proves secure Direct Connect and minimal lobby membership. It has no
gameplay, realtime world transport, reconnect, public server browser, remote
administration, final maps/assets, automatic certificate rotation, bundled Java
runtime, executable, or platform-signed installer. The self-signed certificate
is authenticated by explicit fingerprint pinning, not public PKI.
