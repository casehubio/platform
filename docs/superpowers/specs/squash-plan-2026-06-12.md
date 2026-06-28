# Squash Plan — issue-75-graphiti-erase-domain-case → main
**Range:** `origin/main..HEAD` (10 commits → 2 commits, 8 absorbed)
**Date:** 2026-06-12

---

## Already Clean — 0 commits

---

## Group 1 — SPI break: erase() void → int across all adapters
*Compaction group — 1 commit (standalone KEEP)*

✅ KEEP `127ba70` fix(platform#75): erase(EraseRequest) → int across all adapters

> **Result:** 1 commit (unchanged).

---

## Group 2 — Graphiti domain-scoped erase: full implementation
*Compaction group — 9 commits → 1*

The five spec revision commits, the initial spec, the implementation plan, and
the two test supplement commits all squash into the primary implementation commit.

| Commit | Action | Curated result |
|--------|--------|----------------|
| `62cabed` fix(platform#75): implement GraphitiCaseMemoryStore domain-scoped erase + ERASE_DOMAIN_CASE | ✅ KEEP | *(message adequate — unchanged)* |
| `8658ba7` test(platform#75): add KnownDomains test profile and eraseEntity happy-path tests | 🔽 SQUASH ↑ | *(absorbed — test supplement for same implementation)* |
| `c71af12` test(platform#75): add erase() return value assertion to contract test | 🔽 SQUASH ↑ | *(absorbed — single contract test method)* |
| `98f79eb` docs: graphiti erase domain+caseId design spec (platform#75) | 🔽 SQUASH ↑ | *(absorbed — pre-implementation design doc, superseded by code)* |
| `e179504` docs: revise graphiti erase spec — int return, known-domains, protocol fixes (platform#75) | 🔽 SQUASH ↑ | *(absorbed — spec revision noise)* |
| `5c0c71c` docs: revise graphiti erase spec rev3 — eraseGroup error handling, knownDomains test profile, Mem0 pre-list (platform#75) | 🔽 SQUASH ↑ | *(absorbed — spec revision noise)* |
| `b8039fc` docs: revise graphiti erase spec rev4 — getEpisodesOrEmpty helper, test removals (platform#75) | 🔽 SQUASH ↑ | *(absorbed — spec revision noise)* |
| `90888b0` docs: revise graphiti erase spec rev5 — DELETE episode 404 handling, split 404 tests, mid-loop note (platform#75) | 🔽 SQUASH ↑ | *(absorbed — spec revision noise)* |
| `b163b59` docs: implementation plan for graphiti erase domain+caseId (platform#75) | 🔽 SQUASH ↑ | *(absorbed — pre-implementation planning artifact)* |

> **Result:** 1 commit.

Note on rebase ordering: the 7 squash targets from Group 2 are chronologically BEFORE
`62cabed` in the range. The rebase todo reorders them to appear AFTER `62cabed`.

---

## AFTER — what `git log --oneline` will show

```
10  commits (original)
- 8  absorbed by squash
──────────────────────────────────
 2  commits — no content lost

Expected:
  <sha>  fix(platform#75): implement GraphitiCaseMemoryStore domain-scoped erase + ERASE_DOMAIN_CASE
  <sha>  fix(platform#75): erase(EraseRequest) → int across all adapters
```
