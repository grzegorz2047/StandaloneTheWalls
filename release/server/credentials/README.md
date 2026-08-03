# Credentials

This directory intentionally contains no credentials in the release archive.
On Windows, run root `1_GENERUJ_CREDENTIALS.bat` from the unpacked server
directory. Linux and technical automation can run
`bin/sunderfront-server-credentials --output credentials`. The generator refuses
to overwrite any existing target.

Keep `server-ed25519-key.pk8` secret. Only `server-fingerprint.txt`, the public
certificate, and the public registry-root file may be shared.
