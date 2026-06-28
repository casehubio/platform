# Squash Plan — issue-033-memory-mem0
**Range:** `origin/main..issue-033-memory-mem0` (11 commits → 3)

---

## Group 1 — Design spec (4 commits → 1)

*Compaction group — iterative spec revisions absorbed into first spec commit*

**Final message:** `docs(platform#33): memory-mem0 design spec — Mem0 REST adapter with infer:false`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `aa3eae4` docs(platform#33): memory-mem0 design spec — Mem0 REST adapter with infer:false | ✅ KEEP | *(see Final message above)* |
| `2f7e742` docs(platform#33): revise memory-mem0 spec — infer:false, compound user_id, score-merge, Mem0 OSS API facts | 🔽 SQUASH ↑ | *(absorbed — spec revision round 2; all content folded into final spec)* |
| `9289e18` docs(platform#33): revise memory-mem0 spec v3 — unbounded GET, score incomparability, micrometer-core, drop validator | 🔽 SQUASH ↑ | *(absorbed — spec revision round 3)* |
| `7e54c32` docs(platform#33): revise memory-mem0 spec v4 — threshold config, infer confirmed, since asymmetry, entity-order guidance, 2 new tests | 🔽 SQUASH ↑ | *(absorbed — spec revision round 4; final spec is v4 content)* |

> **Result:** 1 commit.

---

## Group 2 — Module implementation (6 commits → 1)

*Compaction group — all commits implement the same module (issue #33)*

**Final message:** `feat(platform#33): casehub-platform-memory-mem0 — Mem0 REST CaseMemoryStore adapter`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3933606` feat(platform#33): scaffold memory-mem0 Maven module | ✅ KEEP | *(see Final message above)* |
| `2af6e3b` feat(platform#33): add Mem0 REST DTO records for add/search/list API | 🔀 MERGE ↑ | *(unified — DTOs are part of the same module)* |
| `001b08b` feat(platform#33): add Mem0 REST client infrastructure | 🔀 MERGE ↑ | *(unified — Config, AuthFilter, Client, Exception are the module's infrastructure)* |
| `6321f3d` feat(platform#33): test skeleton + Mem0CaseMemoryStore scaffold | 🔀 MERGE ↑ | *(unified — test infrastructure part of the module)* |
| `5239e00` feat(platform#33): implement Mem0CaseMemoryStore — all 33 tests green | 🔀 MERGE ↑ | *(unified — SPI implementation is the module's core)* |
| `7d6f693` fix(platform#33): code review fixes — 3 missing tests, helper visibility, debug log | 🔀 MERGE ↑ | *(unified — code review fixes to the same module before first push)* |

> **Result:** 1 commit.

---

## Group 3 — CLAUDE.md update (1 commit → 1)

*Already clean — no action*

| Commit | Action |
|--------|--------|
| `8e3a839` docs(platform#33): add memory-mem0 module to CLAUDE.md modules table | ✅ KEEP |

> **Result:** 1 commit (unchanged).

---

## AFTER — what `git log --oneline` will show

```
11  commits (original)
 -8  absorbed by squash
──────────────────────────────────────────────
  3  commits — no content lost

Sample (most recent first):
  <sha3>  docs(platform#33): add memory-mem0 module to CLAUDE.md modules table
  <sha2>  feat(platform#33): casehub-platform-memory-mem0 — Mem0 REST CaseMemoryStore adapter
  <sha1>  docs(platform#33): memory-mem0 design spec — Mem0 REST adapter with infer:false
```

---

## Rebase todo (oldest first — as git rebase -i presents)

```
pick aa3eae4 docs(platform#33): memory-mem0 design spec — Mem0 REST adapter with infer:false
squash 2f7e742 docs(platform#33): revise memory-mem0 spec — infer:false, compound user_id, score-merge, Mem0 OSS API facts
squash 9289e18 docs(platform#33): revise memory-mem0 spec v3 — unbounded GET, score incomparability, micrometer-core, drop validator
squash 7e54c32 docs(platform#33): revise memory-mem0 spec v4 — threshold config, infer confirmed, since asymmetry, entity-order guidance, 2 new tests
pick 3933606 feat(platform#33): scaffold memory-mem0 Maven module
squash 2af6e3b feat(platform#33): add Mem0 REST DTO records for add/search/list API
squash 001b08b feat(platform#33): add Mem0 REST client infrastructure
squash 6321f3d feat(platform#33): test skeleton + Mem0CaseMemoryStore scaffold
squash 5239e00 feat(platform#33): implement Mem0CaseMemoryStore — all 33 tests green
squash 7d6f693 fix(platform#33): code review fixes — 3 missing tests, helper visibility, debug log
pick 8e3a839 docs(platform#33): add memory-mem0 module to CLAUDE.md modules table
```
