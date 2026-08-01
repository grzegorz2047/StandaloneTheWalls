/**
 * Bouncy Castle TLS 1.3 adapter primitives for reliable Sunderfront connections.
 *
 * <p>The module enforces TLS 1.3, bounded cipher suites, ALPN, Ed25519 server pinning, and RFC 9266
 * tls-exporter channel binding. It does not implement protocol framing, realtime DTLS, persistence,
 * certificate issuance, or automatic trust changes.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
