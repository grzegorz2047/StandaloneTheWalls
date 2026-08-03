# Credentials

This directory intentionally contains no credentials in the release archive.
Run `bin/sunderfront-server-credentials --output credentials` from the unpacked
server directory. The generator refuses to overwrite any existing target.

Keep `server-ed25519-key.pk8` secret. Only `server-fingerprint.txt`, the public
certificate, and the public registry-root file may be shared.
