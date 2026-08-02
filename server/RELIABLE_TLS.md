# Reliable TLS listener operations

The dedicated server opens its reliable network endpoint only when all three
process configurations are supplied explicitly:

```bash
./gradlew :server:run --args="--config /srv/sunderfront/server.properties --identity-config /srv/sunderfront/identity.properties --tls-config /srv/sunderfront/tls.properties"
```

`--tls-config` always requires `--identity-config`. There is no plaintext,
proof-free, or policy-free fallback. Without `--tls-config` the fixed-tick server
may run for development and administration, but it does not accept network
players.

## Credential files

The process expects two different regular non-symbolic-link files:

- one canonical Ed25519 PKCS#8 DER private key;
- one canonical X.509 DER leaf certificate whose public key matches that private
  key and is currently valid.

The loader reads at most 8 KiB for the private key and 64 KiB for the certificate.
Trailing bytes, another algorithm, a mismatched pair, an expired/not-yet-valid
certificate, a symlink, a directory, or a non-canonical encoding fails before the
listener binds its port. The process never generates a replacement key.

One OpenSSL 3 provisioning example is:

```bash
umask 077
openssl genpkey -algorithm ED25519 -out server-ed25519-key.pem
openssl pkcs8 -topk8 -nocrypt \
  -in server-ed25519-key.pem -outform DER -out server-ed25519-key.pk8
openssl req -new -x509 -key server-ed25519-key.pem \
  -subj "/CN=Sunderfront Dedicated Server" -days 365 \
  -out server-ed25519-certificate.pem
openssl x509 -in server-ed25519-certificate.pem \
  -outform DER -out server-ed25519-certificate.der
rm server-ed25519-key.pem server-ed25519-certificate.pem
chmod 600 server-ed25519-key.pk8
chmod 644 server-ed25519-certificate.der
```

The temporary PEM private key must be removed or protected as carefully as the
DER key. Do not commit either private-key representation. Back up the key through
the operator's established encrypted secret-management process.

## Configuration

Copy `tls.properties.example`. The file is strict UTF-8 literal `key=value` data;
escapes, duplicate keys, unknown keys, edge whitespace, controls, and malformed
numbers are rejected.

Required keys:

```text
transport.schema=1
transport.reliable.private-key-pkcs8-path=<file>
transport.reliable.certificate-x509-path=<file>
```

Relative paths resolve from the TLS configuration directory. The schema value
must be exactly `1`.

Optional listener settings and defaults:

```text
transport.reliable.bind-address=0.0.0.0
transport.reliable.backlog=128
transport.reliable.maximum-concurrent-handshakes=min(16, maximum-active)
transport.reliable.maximum-active-connections=server.maximum-players
transport.reliable.handshake-timeout-seconds=10
transport.reliable.shutdown-timeout-seconds=5
```

The bind address must be a numeric IPv4 or IPv6 literal. Configuration validation
never performs DNS. The listener always uses `server.reliable-port`; there is no
second port setting that could diverge from the advertised server configuration.
Maximum active connections cannot exceed `server.maximum-players`.

Optional identity-exchange settings and defaults:

```text
transport.identity.challenge-lifetime-seconds=30
transport.identity.maximum-outstanding-challenges=maximum-active
transport.identity.result-send-timeout-seconds=5
transport.identity.gateway-shutdown-timeout-seconds=5
```

Challenge capacity cannot exceed active connection capacity. The challenge
lifetime cannot be shorter than the fixed overall Identity Proof V2 exchange
timeout. All values remain subject to the existing protocol/listener hard limits.

## Validation and startup

Validate configuration and credentials without opening SQLite, binding a port,
starting registry refresh, or executing HTTP:

```bash
./gradlew :server:run --args="--config /srv/sunderfront/server.properties --identity-config /srv/sunderfront/identity.properties --tls-config /srv/sunderfront/tls.properties --validate-config"
```

A normal start performs this order:

1. validate server, identity, TLS configuration and credentials;
2. open the one local identity runtime;
3. construct and bind the reliable TLS endpoint;
4. start optional registry refresh;
5. install the process shutdown hook;
6. start accepting TLS connections;
7. start the fixed-tick simulation runtime.

Every accepted lease goes to the mandatory `TlsIdentityAdmissionGateway`. The
gateway performs session bootstrap, channel-bound Identity Proof V2, ban-before-
handle policy, a bounded admission result, and only then transfers an accepted
session into the bounded pre-lobby queue. Network, proof, SQLite, and registry
operations remain outside the fixed-tick thread.

A partial startup failure closes every resource already created. Shutdown first
stops accepts and closes handshakes/active leases/queued sessions, then stops
registry refresh, then stops the simulation runtime. The same ordering applies to
the JVM hook and bounded smoke mode.

## Rotation

Replacing the certificate or private key is an explicit deployment followed by a
process restart. A different public key derives a different public `serverId`.
LAN/private clients therefore detect the pin change and must not accept it
silently. Plan a key rotation by distributing the new fingerprint through a
trusted operator channel before restart; retain the old private key only for the
bounded rollback window defined by local operations policy.

This slice does not implement ACME, public-PKI hostname validation, automatic key
rotation, client reconnect, realtime UDP/DTLS, or realtime session tokens.
