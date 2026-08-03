# Sunderfront dedicated server 0.1.0-alpha.1

This is the first Direct Connect Alpha dedicated server. It requires a 64-bit
Java 21 runtime. No private key, certificate, identity database, trust store, or
registry cache is included in this archive.

## 1. Generate local credentials

Open a terminal in the unpacked directory and run exactly once:

```bash
bin/sunderfront-server-credentials --output credentials
```

On Windows:

```powershell
bin\sunderfront-server-credentials.bat --output credentials
```

The generator refuses to overwrite any target. It creates an Ed25519 PKCS#8
private key, a self-signed X.509 server certificate, a public registry-root file
suitable for the alpha `LOCAL_TOFU` configuration, and `server-fingerprint.txt`.
Protect the private key and back it up together with `data/identity.sqlite`.
Share only the fingerprint with players.

## 2. Validate configuration

```bash
bin/sunderfront-server \
  --config config/server.properties \
  --identity-config config/identity.properties \
  --tls-config config/tls.properties \
  --validate-config
```

Windows uses the same arguments with `bin\sunderfront-server.bat`.

## 3. Start the server

```bash
bin/sunderfront-server \
  --config config/server.properties \
  --identity-config config/identity.properties \
  --tls-config config/tls.properties
```

The reliable TLS listener uses port `27420` by default. Open or forward that TCP
port only when remote players should connect. LAN players can use the server's
LAN address. This alpha does not provide relay, NAT traversal, or discovery.

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
administration, final maps/assets, automatic certificate rotation, or production
registry service. The self-signed certificate is authenticated by explicit
fingerprint pinning, not public PKI.
