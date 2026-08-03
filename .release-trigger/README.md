# Release trigger files

Normal source branches contain only this documentation. A publication branch named
`publish-v<version>` adds one JSON trigger file named `v<version>.json` in one
ordinary commit directly on top of the immutable candidate commit.

For `v0.1.0-alpha.1` the branch-only file is:

```json
{
  "version": "0.1.0-alpha.1",
  "commit": "<40-character current main SHA>"
}
```

The publication workflow rejects additional changed files, extra commits, a stale
or non-main candidate, an existing tag, or an existing Release. Trigger files are
audit records and must never contain credentials, private paths, or runtime data.
