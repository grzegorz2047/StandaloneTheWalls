/**
 * Low-level Bouncy Castle TLS 1.3 adapter for reliable Sunderfront connections.
 *
 * <p>The module owns blocking TLS protocol instances over caller-supplied connected sockets,
 * enforces bounded socket timeouts, TLS 1.3, ALPN, Ed25519 server pinning, and RFC 9266
 * tls-exporter channel binding. It does not implement protocol framing, runtime listener ownership,
 * public PKI, realtime DTLS, persistence, certificate provisioning, or automatic trust changes.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
