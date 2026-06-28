# Squash Plan — 2026-06-15

**Range:** `origin/main..HEAD` (17 commits)
**Working branch:** `squash/wip-main-20260615-025123`
**Strategy:** Strategy E (flat compaction) — no PR merge commits in range

---

## Already Clean — 0 commits

All commits require action.

---

## Action Groups

---

## Group 1 — Design spec: EndpointPermissions + endpoints-config
*Compaction group — 7 commits → 1*
**Final message:** `docs: design spec — EndpointPermissions + casehub-platform-endpoints-config`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `90bfff0` docs: brainstorm spec for platform#89 EndpointPermissions + platform#88 endpoints-config | ✅ KEEP | *(see Final message above)* |
| `8a20384` docs: revise spec for platform#89+#88 — address all 10 review points | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |
| `2e72904` docs: revise spec — address round-2 review (7 points) | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |
| `39702bf` docs: revise spec — address round-3 review (4 points) | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |
| `855127c` docs: revise spec — address round-4 review (3 points) | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |
| `8b55312` docs: revise spec — address round-5 review (config coherence, pseudocode guard, count mismatches) | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |
| `f502fff` docs: revise spec — fix CDI proxy getClass bug, document load() visibility | 🔽 SQUASH ↑ | *(absorbed — spec revision round; same file)* |

> **Result:** 1 commit — complete spec in final form.

---

## Group 2 — platform#89: EndpointPermissions
*Compaction group — 2 commits → 1*

✅ KEEP `d6fff4b` feat(platform#89): add EndpointPermissions.assertTenant() write-auth utility
> Absorbed: `31f4a1c` docs(platform#89): add EndpointPermissions to package structure in CLAUDE.md [row 11: docs(claude) project-useful content]

> **Result:** 1 commit — message adequate.

---

## Group 3 — platform#88: YamlEndpointLoader + module scaffold
*Compaction group — 2 commits → 1*

✅ KEEP `979c8cb` feat(platform#88): implement YamlEndpointLoader
> Absorbed: `92e21e7` chore(platform#88): scaffold endpoints-config module skeleton [row 6: chore → squash forward into next KEEP]

> **Result:** 1 commit — message adequate (scaffold is implementation noise).

---

## Group 4 — platform#88: EndpointConfigLoader complete
*Compaction group — 6 commits → 1*
**Final message:** `feat(platform#88): implement EndpointConfigLoader — @Startup YAML-backed endpoint populator with ${VAR} interpolation, @QuarkusTest CDI integration, and multi-file support`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `2a328c5` feat(platform#88): implement interpolate() and openStream() | ✅ KEEP | *(see Final message above)* |
| `dbc8b36` feat(platform#88): implement EndpointConfigLoader.parseDescriptor() | 🔀 MERGE ↑ | *(unified — Jaccard 1.0 with KEEP; same class, same session)* |
| `c1ef2cf` feat(platform#88): implement EndpointConfigLoader.load() with ifPresent guard | 🔀 MERGE ↑ | *(unified — same class, completes the implementation)* |
| `ff2ae53` test(platform#88): add EndpointConfigLoaderTest @QuarkusTest CDI integration | 🔀 MERGE ↑ | *(unified — tests for same class, same issue, same session)* |
| `dd35514` docs: update CLAUDE.md and ARC42STORIES.MD for platform#88+#89 (C18) | 🔽 SQUASH ↑ | *(absorbed — docs(claude) project-useful content, row 11)* |
| `ecc2998` docs: sync ARC42STORIES.MD — stale scan at session wrap (#69→#70, note C16 closed #34) | 🔽 SQUASH ↑ | *(absorbed — docs follow-on, row 8; stale scan maintenance)* |

> **Result:** 1 commit — richer than any individual message; captures full EndpointConfigLoader capability.

---

## AFTER — what `git log --oneline` will show

```
  17  commits (original)
  -0  pruned by filter-repo
  -13  absorbed by squash/merge
  ──────────────────────────────────────────────
   4  commits — no content lost
```

Simulated sample (actual SHAs assigned after execution):
```
  feat(platform#88): implement EndpointConfigLoader — @Startup YAML-backed...
  feat(platform#88): implement YamlEndpointLoader
  feat(platform#89): add EndpointPermissions.assertTenant() write-auth utility
  docs: design spec — EndpointPermissions + casehub-platform-endpoints-config
```
