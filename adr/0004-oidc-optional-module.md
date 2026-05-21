# 0004 — OIDC-backed CurrentPrincipal in a separate optional module

Date: 2026-05-21
Status: Accepted

## Context and Problem Statement

`casehub-platform` needed a real `@RequestScoped CurrentPrincipal` backed by
Quarkus `SecurityIdentity` and `JsonWebToken`. The existing `platform/` module
holds only `@DefaultBean` mocks. Adding `quarkus-oidc` there would force the
OIDC runtime onto every consumer of `casehub-platform`, including those using
a different auth mechanism or no auth at all.

## Decision Drivers

* `quarkus-oidc` must be strictly opt-in — not every consumer needs it
* The `MockCurrentPrincipal @DefaultBean` must remain the default for dev/test
* A pattern for optional platform implementations already exists (`config/`)
* Duplication of claim-extraction logic across consuming apps should be avoided

## Considered Options

* **Option A** — New `oidc/` module (`casehub-platform-oidc`)
* **Option B** — Add `quarkus-oidc` to the existing `platform/` mock module
* **Option C** — Each consuming app implements `CurrentPrincipal` itself

## Decision Outcome

Chosen option: **Option A**, because it keeps `quarkus-oidc` strictly opt-in,
follows the established `config/` optional-module pattern, and centralises
claim-extraction logic in one place.

### Positive Consequences

* Consumers who don't declare `casehub-platform-oidc` are unaffected — no
  transitive `quarkus-oidc` on their classpath
* CDI displacement is automatic — no exclusion config required
* Jandex plugin and no `@DefaultBean` follow the same conventions as `config/`
* Claim-extraction logic (`tenancyId`, `crossTenantAdmin`) is written once

### Negative Consequences / Tradeoffs

* One additional module to publish and version

## Pros and Cons of the Options

### Option A — New `oidc/` module

* ✅ `quarkus-oidc` is opt-in; consumers not using OIDC are unaffected
* ✅ Follows established `config/` optional-module pattern
* ✅ `MockCurrentPrincipal @DefaultBean` remains active by default
* ❌ One additional artifact to publish

### Option B — Add `quarkus-oidc` to `platform/`

* ✅ No new module
* ❌ Forces `quarkus-oidc` on every consumer of `casehub-platform` transitively
* ❌ Conflates mock module responsibilities with real-auth implementation

### Option C — Consumer-implemented

* ✅ No new module in this repo
* ❌ Claim-extraction logic duplicated across every consuming app
* ❌ Inconsistent behaviour risk as claim names drift between apps

## Links

* `docs/specs/2026-05-21-oidc-current-principal-design.md`
* ADR 0001 — established the optional-module pattern with `config/`
* casehubio/platform#3, #16
