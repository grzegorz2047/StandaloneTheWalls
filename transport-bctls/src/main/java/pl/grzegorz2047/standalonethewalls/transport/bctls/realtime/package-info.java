/**
 * Bounded, process-local ownership of one-time external-PSK tickets for a future DTLS 1.3 realtime
 * adapter.
 *
 * <p>This package defines no socket, handshake or gameplay wire protocol. Tickets are provisioned
 * only by an already authenticated reliable session, atomically consumed before a future DTLS
 * handshake and destroyed on expiry or shutdown.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls.realtime;
