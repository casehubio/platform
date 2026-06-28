# Squash Plan — 2026-06-05

**Range:** `origin/main..HEAD` — 11 commits → 4 commits  
**Mode:** flat compaction (no merge commits; scope-clustered by issue)  
**Filter-repo:** not needed (no blog/HANDOFF paths in range)

---

## Already Clean — 0 commits

All commits are action targets.

---

## Group 1 — Design spec (3 commits → 1)

*Compaction group — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `6dd1bb6` docs(platform#39,platform#49): memory CDI priority and emission design spec | ✅ KEEP | *(message adequate — unchanged)* |
| `e91c625` docs(platform#39,platform#49): revise design spec — address 14 review findings | 🔽 SQUASH ↑ | *(absorbed — spec iteration, all captured in final file)* |
| `dbc7a54` docs(platform#39,platform#49): address second review pass — items A-H | 🔽 SQUASH ↑ | *(absorbed — spec iteration, all captured in final file)* |

> **Result:** 1 commit — `docs(platform#39,platform#49): memory CDI priority and emission design spec`

---

## Group 2 — CDI priority fix (#39, standalone)

*No compaction — 1 commit, already clean*

✅ KEEP `b8fedd1` fix(platform#39): elevate InMemoryMemoryStore to @Priority(10) — test-override tier

> **Result:** 1 commit — unchanged.

---

## Group 3 — Javadoc emission pattern (#49, 2 commits → 1)

*Compaction group — 2 commits → 1*
**Final message:** `docs(platform#49): CaseMemoryStore.store() Javadoc — direct injection canonical, @ObservesAsync unsafe, @Observes acceptable`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `5773e42` docs(platform#49): rewrite CaseMemoryStore.store() Javadoc — direct injection is canonical emission pattern | ✅ KEEP | *(see Final message above)* |
| `804ed19` docs(platform#49): fix Javadoc — @Observes tradeoff framing, add MemoryQuery to link | 🔽 SQUASH ↑ | *(absorbed — Javadoc refinement; context reflected in Final message)* |

> **Result:** 1 commit with enhanced message.

---

## Group 4 — storeAll() implementations (#49, 5 commits → 1)

*Compaction group — 5 commits → 1*
**Final message:** `feat(platform#49): storeAll() — JPA single-transaction batch with per-item assertTenant, NoOp explicit override, contract tests closes #49`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `27a5a7b` feat(platform#49): NoOpCaseMemoryStore — explicit storeAll() returning N × empty string | ✅ KEEP (base — oldest in group) | *(see Final message above)* |
| `82c36e1` test(platform#49): add storeAll() contract tests to CaseMemoryStoreContractTest | 🔀 MERGE ↑ | *(unified — tests paired with implementation)* |
| `fe5b56a` feat(platform#49): JpaMemoryStore.storeAll() — single-transaction batch, per-item assertTenant closes #49 | 🔀 MERGE ↑ | *(unified — primary implementation)* |
| `e2f6550` test(platform#49): clarify storeAll mixed-tenant test comment | 🔽 SQUASH ↑ | *(absorbed — comment clarification, < 5 lines)* |
| `b037c81` test(platform#49): strengthen storeAll ordering test — verify ID maps to correct input via eraseById | 🔽 SQUASH ↑ | *(absorbed — test strengthening, same group)* |

> **Result:** 1 commit with enhanced message. `Closes #49` preserved from fe5b56a.

---

## AFTER — what `git log --oneline` will show

```
11 commits (original)
 - 7 absorbed by squash
─────────────────────────────────────────────
  4 commits — no content lost

Sample (most recent first, estimated SHAs):
  <new-sha>  feat(platform#49): storeAll() — JPA single-transaction batch...
  <new-sha>  docs(platform#49): CaseMemoryStore.store() Javadoc — direct injection canonical...
  <new-sha>  fix(platform#39): elevate InMemoryMemoryStore to @Priority(10) — test-override tier
  <new-sha>  docs(platform#39,platform#49): memory CDI priority and emission design spec
```
