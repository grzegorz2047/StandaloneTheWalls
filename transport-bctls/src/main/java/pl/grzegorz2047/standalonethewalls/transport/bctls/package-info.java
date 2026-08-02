/**
 * Low-level Bouncy Castle TLS 1.3 and reliable-envelope adapters for Sunderfront.
 *
 * <p>The module owns authenticated client/server handshakes, bounded listener admission, a strict
 * fixed-size session UUID bootstrap, fixed-header envelope framing and bounded asynchronous
 * reliable I/O. The server-generated UUIDv4 is confirmed inside TLS before either peer creates an
 * envelope stream; it is a logical identifier, not an authenticator or replacement for
 * tls-exporter. Server-side stream closure owns the full accepted lease and returns listener
 * admission. Identity challenge orchestration, runtime command delivery, public PKI, reconnect,
 * realtime DTLS, persistence and production certificate provisioning remain separate work.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
