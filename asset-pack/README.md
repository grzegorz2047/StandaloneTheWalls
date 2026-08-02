# Asset pack synchronization

Runtime assets are not committed to this repository. `assets/assets.lock.json` is the only reviewed input that selects packs.

## Lock contract

The lock is exact-byte canonical JSON. Each entry pins:

- a lowercase pack ID and canonical `MAJOR.MINOR.PATCH` version;
- a pack format version;
- an immutable HTTPS URL with no `latest`, `current`, `nightly`, or `snapshot` segment;
- exact archive byte length and lowercase SHA-256;
- the in-archive manifest path and its lowercase SHA-256.

Do not edit whitespace or field order manually. Produce the canonical bytes with `AssetPackLockCodec.encode` and review the resulting field changes.

## Synchronize

Run explicitly:

```text
./gradlew :asset-pack:syncAssets
```

The task reads `assets/assets.lock.json` and writes only `.asset-cache/`. Normal `check`, compilation, server startup, and tests do not download production packs.

Synchronization downloads to a temporary file, verifies exact size and SHA-256, validates the ZIP central directory, extracts into a fresh staging directory, verifies the canonical license/file manifest and every file hash, atomically commits the immutable version, then atomically replaces the active pointer.

A failure never replaces the previous pointer. Existing content-addressed versions are verified and reused, never overwritten.

## Offline use

`AssetPackSynchronizer.resolveOffline` reads only the active local pointer and the committed cache tree. It performs no provider call and fails with a bounded code when the pack is missing, stale, conflicting, or corrupt.

## Publishing and provider migration

Publish a versioned archive at a permanent HTTPS URL. Generate its canonical manifest, record the manifest hash, archive size, and archive hash, then update the lock in one reviewable commit. Never replace bytes at a previously locked URL.

A hosting migration is a lockfile change to a new immutable URL with unchanged hashes. The provider interface remains `InputStream open(AssetPackReference)`, so a future mirror, signature layer, or authenticated transport can be added without changing archive verification or cache activation.
