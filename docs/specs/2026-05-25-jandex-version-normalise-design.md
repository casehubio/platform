# Design: Normalise Jandex plugin version — `platform` and `testing` modules

**Issue:** casehubio/platform#25
**Date:** 2026-05-25

## Finding

The core fix for #25 (adding `jandex-maven-plugin` to `platform/pom.xml` so
`@DefaultBean` mocks are discovered in downstream `@QuarkusTest` runs) was
committed in `132c9af`. The issue remained open because the commit message
used `#25` (reference) rather than `closes #25` (auto-close keyword).

Two consistency gaps remain:

| Module | Current version | Should be |
|--------|----------------|-----------|
| `platform/pom.xml` | `3.3.1` (hardcoded literal) | `${jandex-maven-plugin.version}` |
| `testing/pom.xml` | `3.3.1` (hardcoded literal) | `${jandex-maven-plugin.version}` |

All other modules (`config`, `oidc`, `expression`, `persistence-jpa`,
`persistence-mongodb`) already use the property. The parent pom defines
`<jandex-maven-plugin.version>3.3.1</jandex-maven-plugin.version>`, so the
effective value is unchanged — this is a consistency and maintainability fix
only.

## Change

Replace the two hardcoded `3.3.1` literals with `${jandex-maven-plugin.version}`.

## Verification

`mvn --batch-mode install` must pass cleanly. The JARs for `casehub-platform`
and `casehub-platform-testing` must contain `META-INF/jandex.idx`.

## Commit

Commit with `closes #25` so GitHub auto-closes the issue.
