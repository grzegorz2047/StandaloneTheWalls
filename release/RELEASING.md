# Direct Connect Alpha release procedure

The only version handled by this milestone is `v0.1.0-alpha.1`. The build reads
`release/version.txt`; it never discovers or substitutes a moving `latest` value.

## Repository protection required once

Create a GitHub tag ruleset for `v0.1.0-alpha.1` that restricts tag creation and
deletion to repository maintainers. Keep `main` protected and require the normal
CI check before merging the release PR. These settings are administrative and
cannot be enforced by files inside the repository.

## Prepare and publish

1. Merge the release PR after normal CI and distribution E2E are green.
2. Confirm the intended release commit is on `origin/main`.
3. Create the annotated tag exactly at that commit:

   ```bash
   git tag -a v0.1.0-alpha.1 -m "Sunderfront Direct Connect Alpha"
   git push origin v0.1.0-alpha.1
   ```

4. The tag-only workflow repeats `check`, builds each archive twice, compares the
   bytes, verifies archive policy and checksums, and runs Direct Connect from
   freshly unpacked distributions.
5. Publication fails if a release with this tag already exists. It never replaces
   existing assets silently.

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
