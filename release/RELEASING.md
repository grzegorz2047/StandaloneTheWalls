# Interactive Lobby Alpha release procedure

The release workflow in this source version handles only `v0.1.0-alpha.5`. The
build reads `release/version.txt`; it never discovers or substitutes a moving
`latest` value. Published alpha.1 through alpha.4 tags and assets remain immutable
and must not be replaced.

## Repository protection required once

Create a GitHub tag ruleset for `v0.1.0-alpha.5` that permits creation by the
repository Actions identity used by the publication workflow and restricts later
updates or deletion to repository maintainers. Keep `main` protected and require
the normal CI check before merging release changes. These settings are
administrative and cannot be enforced by files inside the repository.

## Prepare the immutable publication branch

1. Merge the release implementation only after PR CI, deterministic UI-font
   generation, Windows app-image checks, and JVM distribution E2E are green.
2. Wait for the push CI on `main`, including `verify-windows-main`, to finish
   successfully. Record that exact current `main` SHA. This is the candidate that
   will be built, tested, tagged, published, and independently audited.
3. Create branch `publish-v0.1.0-alpha.5` directly from that SHA.
4. Add exactly one file, `.release-trigger/v0.1.0-alpha.5.json`, in exactly one
   ordinary commit whose parent is the recorded candidate:

   ```json
   {
     "version": "0.1.0-alpha.5",
     "commit": "<40-character current main SHA>"
   }
   ```

5. Prefer a normal `git push` of the publication branch. Do not merge it into
   `main` and do not add other files or commits.
6. When an automation environment can write repository commits but its push does
   not emit a workflow event, open a same-repository draft PR from
   `publish-v0.1.0-alpha.5` to `main`, review the one-file diff, and mark it ready
   for review. The PR is only a reviewed trigger and audit record; never merge it.

The workflow rejects a branch whose trigger commit is not directly on top of the
candidate, whose diff contains anything except the trigger JSON, whose candidate
is not the current `origin/main`, or whose version does not match
`release/version.txt`. The PR fallback additionally requires the exact publication
branch, a same-repository head, a non-draft PR, and an unchanged head commit through
final publication.

## Automated verification and publication

A push to `publish-v0.1.0-alpha.5`, or marking its reviewed same-repository trigger
PR ready, runs one self-contained workflow:

1. resolve and validate the immutable candidate SHA from the trigger JSON;
2. refuse an existing tag or GitHub Release;
3. on a clean Linux runner, download Andika 6.200 from the official GitHub release,
   verify the pinned source SHA-256, generate the Unicode UI atlas with pinned
   Pillow/fontTools versions, and publish only the verified generated chunks as a
   one-day workflow artifact;
4. run the complete Java 21 quality gate on Ubuntu with that font artifact;
5. build the JVM client and server ZIPs twice, compare them byte-for-byte,
   validate archive policy and SHA-256 checksums, prove a corrupted copy fails,
   and run Direct Connect E2E from freshly unpacked JVM distributions;
6. run the complete Java 21 quality gate on Windows with the same font artifact and
   execute the JVM first-run launchers, including fresh, repeated-complete, partial,
   and mixed-package server credential paths;
7. build the Windows x64 `jpackage` app image twice, run `Sunderfront.exe --smoke`
   and the packaged preparation smoke without external Java before and after
   relocation, compare every app-image file, and create one fixed-timestamp ZIP;
8. upload that already-verified Windows ZIP as a short-lived workflow artifact;
9. rebuild the final JVM payload from the same candidate on Linux, download the
   verified Windows ZIP from the same workflow, assemble one sorted `SHA256SUMS`,
   and run the full archive-policy validator with Windows required;
10. recheck that `main`, the publication branch, the tag, and the Release have not
    changed;
11. run `gh release create --target <candidate SHA>`, which creates
    `v0.1.0-alpha.5` and the prerelease only after every gate succeeds;
12. verify that the tag resolves to the candidate and the Release contains exactly:
    - `sunderfront-client-windows-x64-0.1.0-alpha.5.zip`;
    - `sunderfront-client-0.1.0-alpha.5.zip`;
    - `sunderfront-server-0.1.0-alpha.5.zip`;
    - `SHA256SUMS`;
13. start a separate clean audit job that downloads those four published assets
    from GitHub, rechecks the tag target and exact asset set, validates
    `SHA256SUMS`, and runs `release/verify_artifacts.py --require-windows` to reject
    secrets, credentials, identity stores, caches, runtime data, malformed ZIPs,
    missing M2 instructions, and unexpected files.

The publication branch is the explicit audit trigger, while the Release targets a
separately validated source commit. There is no `workflow_run` chain, recursive
workflow dispatch, manually pre-created tag, moving URL, or post-verification
rebuild of the Windows archive.

A missing tag or a failed independent audit is a fail-closed signal that publication
did not complete successfully. Repair the failed gate or publish a new version;
never create or move the tag around the workflow.

## Verify downloaded artifacts

Place all three ZIP files beside `SHA256SUMS`.

On Linux or macOS:

```bash
sha256sum --check SHA256SUMS
python3 release/verify_artifacts.py . 0.1.0-alpha.5 --require-windows
```

On Windows PowerShell:

```powershell
Get-FileHash .\sunderfront-client-windows-x64-0.1.0-alpha.5.zip -Algorithm SHA256
Get-FileHash .\sunderfront-client-0.1.0-alpha.5.zip -Algorithm SHA256
Get-FileHash .\sunderfront-server-0.1.0-alpha.5.zip -Algorithm SHA256
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

Never reuse `v0.1.0-alpha.5` for different bytes.
