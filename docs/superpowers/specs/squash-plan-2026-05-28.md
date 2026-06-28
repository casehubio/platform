# Squash Plan — issue-37-memory-sqlite → origin/main
Date: 2026-05-31
Range: origin/main..HEAD (13 commits → 3)

---

## Already Clean — 0 commits

All 13 commits have squash actions.

---

## Group 1 — Design spec

*2 commits → 1*
**Final message:** `docs(#37): memory-sqlite design spec`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3b2d6ac` docs(#37): memory-sqlite design spec | ✅ KEEP | *(see Final message above)* |
| `b975059` docs(#37): revise memory-sqlite spec — review fixes | 🔽 SQUASH ↑ | *(absorbed — spec revision fixup)* |

> **Result:** 1 commit.

---

## Group 2 — Contract test base refactor

*4 commits → 1*
**Final message:** `refactor(#37): extract CaseMemoryStoreContractTest; update inmem and JPA test adapters`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3e6f288` refactor(#37): extract CaseMemoryStoreContractTest abstract base | ✅ KEEP | *(see Final message above)* |
| `468ac60` refactor(#37): fix contract test base quality | 🔽 SQUASH ↑ | *(absorbed — quality fixup to base class)* |
| `9b7653a` refactor(#37): InMemoryMemoryStoreTest extends CaseMemoryStoreContractTest | 🔀 MERGE ↑ | *(unified — same refactor concern, same issue)* |
| `ee6c8b9` refactor(#37): JpaMemoryStoreTest extends CaseMemoryStoreContractTest | 🔀 MERGE ↑ | *(unified — same refactor concern, same issue)* |

> **Result:** 1 commit.

---

## Group 3 — memory-sqlite module

*7 commits → 1*
**Final message:** `feat(#37): memory-sqlite — SQLite CaseMemoryStore; HikariCP + WAL + FTS5 + programmatic Flyway`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cf7dfd5` feat(#37): add memory-sqlite module; pin sqlite-jdbc and HikariCP versions | ✅ KEEP | *(see Final message above)* |
| `6329891` feat(#37): memory-sqlite schema migration, test config, and test skeleton | 🔀 MERGE ↑ | *(unified — same module, scaffolding)* |
| `5043b3f` feat(#37): SqliteMemoryStore — HikariCP + Flyway + all CaseMemoryStore SPI methods | 🔀 MERGE ↑ | *(unified — main implementation)* |
| `4a65817` feat(#37): FTS5 query path + SQLite-specific contract tests | 🔀 MERGE ↑ | *(unified — FTS5 path, same module)* |
| `105a83b` fix(#37): remove duplicate FTS-disabled test; clarify per-item assertTenant | 🔽 SQUASH ↑ | *(absorbed — test fixup)* |
| `550e41d` docs(#37): add memory-sqlite to CLAUDE.md modules table | 🔽 SQUASH ↑ | *(absorbed — docs follow-on)* |
| `6a8d415` fix(#37): since filter >= parity; fts.enabled=false test profile; TypeReference; CLAUDE.md | 🔽 SQUASH ↑ | *(absorbed — review fixes)* |

> **Result:** 1 commit.

---

## AFTER

```
13 commits (original)
-10 absorbed by squash/merge
──────────────────────────────────────────────────
3 commits — no content lost
```

Sample (most recent first):
  feat(#37): memory-sqlite — SQLite CaseMemoryStore; HikariCP + WAL + FTS5 + programmatic Flyway
  refactor(#37): extract CaseMemoryStoreContractTest; update inmem and JPA test adapters
  docs(#37): memory-sqlite design spec
