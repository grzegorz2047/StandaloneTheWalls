# Credentials

This directory intentionally contains no credentials in the release archive.
On Windows, run root `1_GENERUJ_CREDENTIALS.bat` from the unpacked server
directory. The root launcher accepts an existing complete non-empty set without
changing it, fails closed for a partial set, and rejects an incomplete or
mixed-version package.

Linux and technical automation can run
`bin/sunderfront-server-credentials --output credentials`. The technical
generator remains strictly no-overwrite and is intended for an empty target.

Keep `server-ed25519-key.pk8` secret. Only `server-fingerprint.txt`, the public
certificate, and the public registry-root file may be shared. Back up and restore
all four credential files as one identity set; never combine individual files
from different generator runs.
