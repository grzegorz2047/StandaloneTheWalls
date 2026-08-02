# ADR 0028: Local display names are presentation-only

- Status: Accepted
- Date: 2026-08-02
- Issue: #77
- Depends on: ADR 0015, ADR 0016, ADR 0017, ADR 0021

## Context

A stable cryptographic `playerId` and canonical handle already have explicit
security roles. Operators also need a local, human-friendly Unicode alias for
logs and future UI. Treating that alias as another identifier would introduce
Unicode collision, normalization, impersonation, and reservation ambiguity into
`LOCAL_TOFU`, `GLOBAL_ONLY`, and `HYBRID`.

The value must be safe to persist and render later, but this slice must not add UI,
network fields, lobby behavior, chat, registry claims, or runtime composition.
Administrative writes also need the same compare-and-set and atomic audit
boundary used by existing local identity state.

## Decision

Add `LocalDisplayName`, a renderer-independent immutable value in
`identity-policy`, and assign it directly to a public stable `playerId`.

The canonical handle remains the only name used for authorization, local TOFU
ownership, global registry lookup, and registry reservation. Display names:

- never enter `HandleAuthorizationService` or `SessionIdentityAdmissionService`;
- never replace or derive a canonical handle;
- never enter registry snapshot v1 or claim formats;
- do not need local or global uniqueness;
- grant no binding, reservation, verification level, or admission right.

Two player IDs may therefore have the same display name.

### Unicode contract

Construction performs, in order:

1. reject input above 512 UTF-16 code units, malformed UTF-16, and prohibited raw
   code points;
2. normalize to Unicode NFC;
3. trim Unicode whitespace/space characters from both ends;
4. reject an empty result;
5. enforce at most 64 Unicode code points;
6. enforce at most 192 bytes in UTF-8.

NUL, controls, surrogate code points, unassigned code points, line/paragraph
separators, and all Unicode format characters are prohibited. Rejecting the
format category includes bidi overrides, bidi isolates, zero-width joiners,
zero-width non-joiners, and other invisible formatting controls. Errors use
bounded semantic messages and never include the supplied value.

No case folding, confusable skeleton, transliteration, locale mapping, or custom
"canonical display name" is created.

### Administration contract

`LocalDisplayNameAdministrationStore` exposes lookup by `playerId`, a bounded
list sorted by `playerId`, set, clear, and append-only audit reads.

Every mutation includes exactly one optimistic-concurrency expectation:

- `ABSENT`;
- `PRESENT`;
- `EXACT(previousDisplayName)`.

There is no unconditional last-write-wins operation. Stable results are
`APPLIED`, `UNCHANGED`, `NOT_FOUND`, `EXPECTATION_MISMATCH`, `INVALID_VALUE`, and
`CAPACITY_EXCEEDED`. Invalid raw values are rejected by the service before the
store is called. A binding-not-found result is intentionally absent: a valid
player ID admitted globally may have a local display name, and the name itself
must not create a local handle binding.

Every applied set or clear creates exactly one audit event with a positive
monotonic sequence, administrator ID, timestamp, player ID, previous value, new
value, action, and bounded reason. No-op or failed attempts create no event. If
state or audit capacity is exhausted, the mutation is rejected.

### SQLite v3

`identity-policy-sqlite` migrates schema v2 to v3 in one `BEGIN IMMEDIATE`
transaction. It creates:

- `local_player_display_names`, keyed by `player_id` without a unique display-name
  constraint;
- `local_player_display_name_audit` with action-shape checks;
- update and delete triggers that make the audit table append-only;
- metadata version 3 only after all objects are created.

Display-name mutation and audit insertion use one transaction. Any SQL or audit
failure rolls both back. Existing handle bindings, player bans, handle audit,
ban audit, and their independent sequence values are untouched. Newer versions
and incomplete v3 schemas fail closed.

## Consequences

- Future renderers may show a friendly alias without weakening identity policy.
- Duplicate aliases are expected and cannot be used to infer ownership.
- Authorization tests can prove isolation because the display-name store is not a
  dependency of any authorization mode or the ban-before-handle gate.
- Operators get deterministic compare-and-set updates and durable audit without
  an unaudited last-write-wins path.
- UI, protocol fields, commands, `ServerLauncher`, `LocalIdentityRuntime`, lobby,
  chat, nameplates, and registry publishing remain separate work.
