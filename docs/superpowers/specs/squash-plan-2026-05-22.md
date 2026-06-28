# Squash Plan — casehub-platform — 2026-05-22

**Working branch:** `squash/wip-main-20260522-211312`
**Base:** `--root` (full history, 62 commits)
**Result:** 62 → 52 commits (10 absorbed)

---

## Already Clean — 52 commits (no action)

All feat, adr, fix, test, ci, build, refactor, protocol, and docs introducing new
documents that carry issue refs or are substantive standalone entries.

Representative capabilities: bootstrap, CLAUDE.md, Path, Preferences, CurrentPrincipal,
@DefaultBean mocks, ADRs 0001–0007, protocols, testing module, config module, OIDC,
expression, JPA persistence, MongoDB persistence, path root, jandex fix, root-scope tests.

---

## Action Groups

### Group 1 — Platform-API design spec + CDI scope note
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9ec7207` docs(epic-platform-api): add platform-api design spec — #1 | ✅ KEEP | *(message adequate)* |
| `cbf5dd7` docs(epic-platform-api): add CurrentPrincipal CDI scope note — #1 | 🔽 SQUASH ↑ | *(absorbed — doc note added to spec; same issue, same doc)* |

> **Result:** 1 commit.

---

### Group 2 — DefaultBean mocks + groups config docs follow-on
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9e9e2ac` feat(platform): implement @DefaultBean mocks + @QuarkusTest — #1 | ✅ KEEP | *(message adequate)* |
| `eb4075a` docs(platform): document groups config property in test application.properties — #1 | 🔀 MERGE ↑ | *(1-line config doc immediately following the feat for the same issue)* |

> **Result:** 1 commit. Conflict risk: low (1-line addition to file already touched by feat).

---

### Group 3 — Code review fixes + two CLAUDE.md follow-ons
*Compaction — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `ab93a61` fix(review): misleading test name, null behaviour, multi-value parse test — #4 | ✅ KEEP | *(message adequate)* |
| `6a0ccc0` docs: update CLAUDE.md — add testing/ module, fix package names, add Name field | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md update following testing module work)* |
| `66d7cb1` docs: add writing style guide pointer to CLAUDE.md | 🔽 SQUASH ↑ | *(absorbed — methodology pointer, no feature connection)* |

> **Result:** 1 commit. Conflict risk: none (different files).

---

### Group 4 — Path ParamConverter + Javadoc clarification
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `ab0df85` feat(platform): add Path JAX-RS ParamConverter and ParamConverterProvider — #12 | ✅ KEEP | *(message adequate)* |
| `cdb94ab` docs(preferences): clarify PreferenceKey.parse() Javadoc — #10 | 🔽 SQUASH ↑ | *(absorbed — Javadoc clarification, different issue but nearest KEEP)* |

> **Result:** 1 commit. Conflict risk: none (different files).

---

### Group 5 — Config module feat + CLAUDE.md + build registration
*Compaction — 3 commits → 1*
**Final message:** `feat(config): implement ConfigFilePreferenceProvider — scope-aware YAML + SmallRye overrides, registered in root pom — #5`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `07309258` feat(config): implement ConfigFilePreferenceProvider — scope-aware YAML + SmallRye overrides — #5 | ✅ KEEP | *(see Final message above)* |
| `64e0abb` docs: update CLAUDE.md — add config/ module to Modules table and Rules | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md follow-on after config feat)* |
| `e4ccf96` build(maven): register testing and config modules in root pom | 🔽 SQUASH ↑ | *(absorbed — build registration follows module creation)* |

> **Result:** 1 commit. Conflict risk: none (different files — root pom vs config Java files).

---

### Group 6 — OIDC design spec merged into implementation
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9e29d0e` feat(oidc): add casehub-platform-oidc module — @RequestScoped CurrentPrincipal #3 #16 | ✅ KEEP | *(message adequate — reordered before spec in todo)* |
| `a651e01` docs(spec): OidcCurrentPrincipal design — casehub-platform-oidc module #3 #16 | 🔀 MERGE ↑ | *(spec preceded implementation; absorbed into the feat it describes)* |

> **Result:** 1 commit. Conflict risk: none (spec touches docs/specs/, feat touches oidc/src/).

---

### Group 7 — Protocol refactor + routing docs follow-on
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9626d5e` refactor(protocols): remove local protocol store — route all protocols to casehub/parent | ✅ KEEP | *(message adequate)* |
| `ec03398` refactor(docs): update protocol routing to casehub/garden | 🔽 SQUASH ↑ | *(absorbed — docs follow-on to the same routing change)* |

> **Result:** 1 commit. Conflict risk: low (ec03398 likely touches CLAUDE.md; 9626d5e removes docs/protocols/ files — different).

---

### Group 8 — MongoDB feat + CLAUDE.md follow-on
*Compaction — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `8b67c6c` feat(persistence): add casehub-platform-persistence-mongodb module #7 | ✅ KEEP | *(message adequate)* |
| `a57e183` docs: add missing modules to CLAUDE.md Modules table | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md update immediately following mongodb module addition)* |

> **Result:** 1 commit. Conflict risk: none (different files).

---

## AFTER — estimated `git log --oneline` sample

```
62  commits (original)
-10 absorbed by squash
──────────────────────────────────────────────
52  commits — no content lost

Sample (most recent, post-squash):
  <new> docs: promote spec from issue-26-batch-housekeeping ...
  <new> docs: update PreferenceProvider Javadoc (#26)
  <new> test: add root-scope contract tests and fix ancestors() (#26)
  <new> docs: promote persistence-mongodb design spec from workspace
  <new> feat(persistence): add casehub-platform-persistence-mongodb module #7  [absorbs a57e183]
  <new> fix(platform): add Jandex index for downstream @QuarkusTest bean discovery #25
  <new> test(path): cache Path.root() singleton and add root contract tests
  <new> feat(path): add Path.root() for zero-segment root scope ...
  <new> adr: 0007 sla-breach-policy-in-work-api-not-platform
  <new> adr: 0006 jpa-preference-provider-current-only
```

*Run `git log --oneline squash/wip-main-...` after execution to verify.*

---

## Execution note

Conflict strategy: if any squash raises a non-trivial conflict, the operation
will be aborted for that group and those commits left as separate KEEPs.
Groups 1, 3, 4, 5, 6, 7, 8 are expected clean. Group 2 is low risk (1-line merge).
