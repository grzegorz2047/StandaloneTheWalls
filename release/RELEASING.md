# Direct Connect Alpha release procedure

The only version handled by this milestone is `v0.1.0-alpha.1`. The build reads
`release/version.txt`; it never discovers or substitutes a moving `latest` value.

## Repository protection required once

Create a GitHub tag ruleset for `v0.1.0-alpha.1` that restricts tag creation and
deletion to repository maintainers and the repository Actions identity used by the
promotion workflow. Keep `main` protected and require the normal CI check before
merging release changes. These settings are administrative and cannot be enforced
by files inside the repository.

## Automated promotion and publication

1. Merge release changes only after PR CI and distribution E2E are green.
2. A push to `main` starts `Promote Direct Connect Alpha Tag`.
3. If `v0.1.0-alpha.1` already exists, promotion exits successfully without moving,
   replacing, or rebuilding the tag.
4. When the tag is absent, promotion independently runs:
   - the complete Java 21 quality gate on Ubuntu;
   - two-build reproducibility, archive policy, checksum checks, negative checksum
     verification, and Direct Connect E2E from freshly unpacked distributions;
   - the complete Java 21 quality gate on Windows.
5. Only after all three gates succeed, promotion proves `origin/main` still equals
   the exact triggering commit, rechecks tag absence, and creates annotated
   `v0.1.0-alpha.1` on that commit.
6. Promotion dispatches `Publish Direct Connect Alpha`. Publication checks out the
   existing exact tag, proves it points to a commit on `main`, reruns `check` and
   the complete release verification, and refuses to replace an existing Release.
7. The prerelease contains exactly the client ZIP, server ZIP, `SHA256SUMS`, release
   notes, and known alpha limitations.

Do not create or move the release tag manually during the normal process. A missing
tag is a fail-closed signal that one of the promotion gates has not completed
successfully. Investigate and repair the gate; do not publish around it.

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
