/**
 * Low-level Bouncy Castle TLS 1.3 and reliable-envelope adapters for Sunderfront.
 *
 * <p>The module owns blocking TLS protocol instances, authenticated server/client handshakes,
 * strict fixed-header framing, session binding, gap-free sequences and bounded asynchronous
 * reliable I/O. `Tls13ServerListener` additionally owns a bound endpoint, a dedicated accept
 * thread, named virtual handshake threads, hard handshake/active-connection admission limits and
 * tracked active leases with bounded shutdown. It intentionally stops at an authenticated
 * `AcceptedTlsConnection`: session UUID bootstrap, runtime command delivery, public PKI, reconnect,
 * realtime DTLS, persistence and production certificate provisioning remain separate work.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;
