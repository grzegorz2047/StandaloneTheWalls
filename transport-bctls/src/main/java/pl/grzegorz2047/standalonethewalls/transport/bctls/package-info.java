/**
 * Low-level Bouncy Castle TLS 1.3 and reliable-envelope adapters for Sunderfront.
 *
 * <p>The module owns blocking TLS protocol instances over caller-supplied connected sockets,
 * enforces bounded socket timeouts, TLS 1.3, ALPN, Ed25519 server pinning, RFC 9266 tls-exporter
 * channel binding, fixed-header framing, session binding, and gap-free per-direction sequences.
 * `AsyncTlsReliableChannel` moves the blocking envelope stream onto owned named virtual threads,
 * enforces count and byte admission limits, permits one receive, and terminates its executor
 * through an asynchronous bounded close. It does not implement runtime listener ownership, public
 * PKI, realtime DTLS, persistence, certificate provisioning, reconnect, or automatic trust changes.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
