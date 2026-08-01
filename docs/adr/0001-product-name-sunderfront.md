# ADR 0001: Use Sunderfront as the working product name

- Status: accepted as a working name
- Date: 2026-08-01

## Context

`StandaloneTheWalls` accurately describes the repository's origin but is weak as
a public product name, remains closely tied to Minecraft terminology, and does
not communicate an independent visual identity.

The game centers on four protected fronts that are deliberately broken open,
turning preparation into a shared battlefield and eventually a final arena.

## Decision

Use **Sunderfront** as the working player-facing product name. Keep the existing
GitHub repository name temporarily to avoid unnecessary link and automation
churn during foundation work.

Internal Java package names are not renamed in this ADR. A later focused change
may migrate identifiers after the branding and namespace review is complete.

## Consequences

- New player-facing documentation uses Sunderfront.
- The game must develop its own logo, UI, visual language, terminology, and asset
  identity rather than imitate Minecraft.
- Before a public commercial release, perform a formal trademark, store, domain,
  package namespace, and social-handle review. The preliminary exact-name search
  performed during planning is not legal clearance.
- If the name changes before release, the architecture and protocol must avoid
  unnecessary dependence on the display brand. Protocol domain separators and
  stable IDs require explicit migration/versioning rather than silent edits.
