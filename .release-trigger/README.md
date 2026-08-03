# Release trigger files

Normal source branches contain only this documentation. A publication branch named
`publish-v<version>` adds one JSON trigger file named `v<version>.json` in one
ordinary commit directly on top of the immutable candidate commit.

For `v0.1.0-alpha.2` the branch-only file is:

```json
{
  "version": "0.1.0-alpha.2",
  "commit": "<40-character current main SHA>"
}
```

A normal `git push` of the publication branch starts the workflow directly. When an
automation environment can create repository commits but its push does not emit a
workflow event, open a same-repository draft PR from the exact publication branch to
`main`, review the one-file diff, and mark it ready for review. The PR is an explicit
trigger and audit record; it must never be merged.

The publication workflow rejects additional changed files, extra commits, a stale
or non-main candidate, a version mismatch, an existing tag, or an existing Release.
For a PR trigger it additionally requires the exact head branch, the same repository,
a non-draft state, and an unchanged publication head through final publication.
Trigger files must never contain credentials, private paths, or runtime data.
