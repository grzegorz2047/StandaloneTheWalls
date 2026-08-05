# wolfSSL DTLS 1.3 provider spike

This directory contains non-production proof work for issue #167.

The spike builds exact upstream commits of native wolfSSL and wolfSSL JNI/JSSE on GitHub-hosted runners. It does not add wolfSSL to the Gradle dependency graph, release archives, runtime classpath or application license notices.

## Current proof stage

The first stage verifies that the pinned Java/JNI API exposes all primitives required for a later external-PSK loopback:

- dedicated DTLS 1.3 client and server methods;
- client and server PSK callbacks;
- DTLS peer binding and datagram socket ownership;
- explicit handshake and cleanup methods;
- reproducible JAR and native-library checksums.

A green API proof does not select wolfSSL and does not satisfy issue #165. A subsequent stage must prove a real one-time external-PSK DTLS 1.3 loopback, replay rejection, no DTLS 1.2 downgrade, cookie admission, Linux and Windows packaging, and an explicit license decision.

## License boundary

The project remains MIT. Upstream wolfSSL offers open-source and commercial licensing options. No wolfSSL binary or source is distributed by this spike, and no product dependency may be merged until the project owner records an explicit licensing decision.
