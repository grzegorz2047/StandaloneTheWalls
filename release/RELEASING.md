# Direct Connect Alpha release procedure

The only version handled by this milestone is `v0.1.0-alpha.1`. The build reads
`release/version.txt`; it never discovers or substitutes a moving `latest` value.

## Repository protection required once

Create a GitHub tag ruleset for `v0.1.0-alpha.1` that permits creation by the
repository Actions identity used by the publication workflow and restricts later
updates or deletion to repository maintainers. Keep `main` protected and require
the normal CI check before merging release changes. These settings are
administrative and cannot be enforced by files inside the repository.

## Prepare the immutable publication branch

1. Merge the release implementation only after PR CI and distribution E2E are
   green.
2. Record the exact current `main` SHA. This is the candidate that will be built,
   tested, tagged, and published.
3. Create branch `publish-v0.1.0-alpha.1` directly from that SHA.
4. Add exactly one file, `.release-trigger/v0.1.0-alpha.1.json`, in exactly one
   ordinary commit whose parent is the recorded candidate:

   ```json
   {
     "version": "0.1.0-alpha.1",
     "commit": "<40-character current main SHA>"
   }
   ```

5. Prefer a normal `git push` of the publication branch. Do not merge it into
   `main` and do not add other files or commits.
6. When an automation environment can write repository commits but its push does
   not emit a workflow event, open a same-repository draft PR from
   `publish-v0.1.0-alpha.1` to `main`, review the one-file diff, and mark it ready
   for review. The PR is only a reviewed trigger and audit record; never merge it.

The workflow rejects a branch whose trigger commit is not directly on top of the
candidate, whose diff contains anything except the trigger JSON, whose candidate
is not the current `origin/main`, or whose version does not match
`release/version.txt`. The PR fallback additionally requires the exact publication
branch, a same-repository head, a non-draft PR, and an unchanged head commit through
final publication.

## Automated verification and publication

A push to `publish-v0.1.0-alpha.1`, or marking its reviewed same-repository trigger
PR ready, runs one self-contained workflow:

1. resolve and validate the immutable candidate SHA from the trigger JSON;
2. refuse an existing tag or GitHub Release;
3. run the complete Java 21 quality gate on Ubuntu;
4. build the client and server ZIPs twice, compare them byte-for-byte, validate
   archive policy and SHA-256 checksums, prove a corrupted copy fails checksum
   verification, and run Direct Connect E2E from freshly unpacked distributions;
5. run the complete Java 21 quality gate on Windows;
6. rebuild the final payload from the same candidate and recheck that `main`, the
   publication branch, the tag, and the Release have not changed;
7. run `gh release create --target <candidate SHA>`, which creates
   `v0.1.0-alpha.1` and the prerelease only after every gate succeeds;
8. verify that the resulting tag resolves to the candidate and that the Release
   contains exactly the client ZIP, server ZIP, and `SHA256SUMS`.

This follows the release-branch model used by `grzegorz2047/3dsTowerdefenseAIDev`:
the publication branch is the explicit audit trigger, while the Release targets a
separately validated source commit. There is no `workflow_run` chain, recursive
workflow dispatch, or manually pre-created tag.

A missing tag is a fail-closed signal that publication did not complete. Repair the
failed gate or workflow; never create or move the tag around it.

## Verify downloaded artifacts

On Linux or macOS, place the two ZIP files beside `SHA256SUMS` and run:

```bash
sha256sum --check SHA256SUMS
```

On Windows PowerShell, compare each value with:

```powershell
Get-FileHash .\sunderfront-client-0.1.0-alpha.1.zip -Algorithm SHA256
Get-FileHash .\sunderfront-server-0.1.0-alpha.1.zip -Algorithm SHA256
```

The names and lowercase hashes must match `SHA256SUMS` exactly.

## Rollback or yank an alpha

Published bytes are immutable. Do not upload replacement files under the same
tag. When an alpha must be withdrawn:

1. edit the GitHub Release title and notes to begin with `YANKED` and describe the
   reason without exposing credentials or user data;
2. mark the release as a prerelease if it is not already one;
3. leave the existing assets and checksums available for audit unless a security
   or legal incident requires removal;
4. fix the problem on `main` and publish a new version/tag through a new issue;
5. delete the tag only for an incident requiring removal, using the protected tag
   ruleset and recording the decision in the replacement release notes.

Never reuse `v0.1.0-alpha.1` for different bytes.
