# Java-Free Windows Client Alpha release procedure

The release workflow in this source version handles only `v0.1.0-alpha.3`. The
build reads `release/version.txt`; it never discovers or substitutes a moving
`latest` value. Published alpha.1 and alpha.2 tags and assets remain immutable and
must not be replaced.

## Repository protection required once

Create a GitHub tag ruleset for `v0.1.0-alpha.3` that permits creation by the
repository Actions identity used by the publication workflow and restricts later
updates or deletion to repository maintainers. Keep `main` protected and require
the normal CI check before merging release changes. These settings are
administrative and cannot be enforced by files inside the repository.

## Prepare the immutable publication branch

1. Merge the release implementation only after PR CI, Windows app-image checks,
   and JVM distribution E2E are green.
2. Record the exact current `main` SHA. This is the candidate that will be built,
   tested, tagged, and published.
3. Create branch `publish-v0.1.0-alpha.3` directly from that SHA.
4. Add exactly one file, `.release-trigger/v0.1.0-alpha.3.json`, in exactly one
   ordinary commit whose parent is the recorded candidate:

   ```json
   {
     "version": "0.1.0-alpha.3",
     "commit": "<40-character current main SHA>"
   }
   ```

5. Prefer a normal `git push` of the publication branch. Do not merge it into
   `main` and do not add other files or commits.
6. When an automation environment can write repository commits but its push does
   not emit a workflow event, open a same-repository draft PR from
   `publish-v0.1.0-alpha.3` to `main`, review the one-file diff, and mark it ready
   for review. The PR is only a reviewed trigger and audit record; never merge it.

The workflow rejects a branch whose trigger commit is not directly on top of the
candidate, whose diff contains anything except the trigger JSON, whose candidate
is not the current `origin/main`, or whose version does not match
`release/version.txt`. The PR fallback additionally requires the exact publication
branch, a same-repository head, a non-draft PR, and an unchanged head commit through
final publication.

## Automated verification and publication

A push to `publish-v0.1.0-alpha.3`, or marking its reviewed same-repository trigger
PR ready, runs one self-contained workflow:

1. resolve and validate the immutable candidate SHA from the trigger JSON;
2. refuse an existing tag or GitHub Release;
3. run the complete Java 21 quality gate on Ubuntu;
4. build the JVM client and server ZIPs twice, compare them byte-for-byte,
   validate archive policy and SHA-256 checksums, prove a corrupted copy fails,
   and run Direct Connect E2E from freshly unpacked JVM distributions;
5. run the complete Java 21 quality gate on Windows and execute the JVM first-run
   launchers;
6. build the Windows x64 `jpackage` app image twice, run `Sunderfront.exe --smoke`
   without external Java before and after relocation, compare every app-image file,
   and create one fixed-timestamp deterministic ZIP;
7. upload that already-verified Windows ZIP as a short-lived workflow artifact;
8. rebuild the final JVM payload from the same candidate on Linux, download the
   verified Windows ZIP from the same workflow, assemble one sorted `SHA256SUMS`,
   and run the full archive-policy validator with Windows required;
9. recheck that `main`, the publication branch, the tag, and the Release have not
   changed;
10. run `gh release create --target <candidate SHA>`, which creates
    `v0.1.0-alpha.3` and the prerelease only after every gate succeeds;
11. verify that the tag resolves to the candidate and the Release contains exactly:
    - `sunderfront-client-windows-x64-0.1.0-alpha.3.zip`;
    - `sunderfront-client-0.1.0-alpha.3.zip`;
    - `sunderfront-server-0.1.0-alpha.3.zip`;
    - `SHA256SUMS`.

The publication branch is the explicit audit trigger, while the Release targets a
separately validated source commit. There is no `workflow_run` chain, recursive
workflow dispatch, manually pre-created tag, moving URL, or post-verification
rebuild of the Windows archive.

A missing tag is a fail-closed signal that publication did not complete. Repair the
failed gate or workflow; never create or move the tag around it.

## Verify downloaded artifacts

Place all three ZIP files beside `SHA256SUMS`.

On Linux or macOS:

```bash
sha256sum --check SHA256SUMS
```

On Windows PowerShell:

```powershell
Get-FileHash .\sunderfront-client-windows-x64-0.1.0-alpha.3.zip -Algorithm SHA256
Get-FileHash .\sunderfront-client-0.1.0-alpha.3.zip -Algorithm SHA256
Get-FileHash .\sunderfront-server-0.1.0-alpha.3.zip -Algorithm SHA256
```

The names and lowercase hashes must match `SHA256SUMS` exactly. Windows players
should use the archive containing `client-windows-x64`; the plain client archive
is the technical JVM distribution.

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

Never reuse `v0.1.0-alpha.3` for different bytes.
