/**
 * Low-level Bouncy Castle TLS 1.3 and strict reliable-envelope adapters for Sunderfront.
 *
 * <p>The module owns blocking TLS protocol instances over caller-supplied connected sockets,
 * enforces bounded socket timeouts, TLS 1.3, ALPN, Ed25519 server pinning, RFC 9266 tls-exporter
 * channel binding, fixed-header framing, session binding, and gap-free per-direction sequences. The
 * blocking envelope layer permits one concurrent reader and writer but owns no executor or hidden
 * thread. It does not implement asynchronous channel executors, runtime listener ownership, public
 * PKI, realtime DTLS, persistence, certificate provisioning, or automatic trust changes.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
