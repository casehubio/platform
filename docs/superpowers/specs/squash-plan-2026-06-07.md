# Squash Plan — issue-69-mem0-storeall-batch
*2026-06-07 · range: origin/main..HEAD · 6 commits → 3*

## Already Clean — 0 commits (no action needed)

## Action Groups

---

## Group 1 — Spec document (2 → 1)

✅ KEEP `e65373f` docs(platform#69): spec — Mem0 storeAll() sequential batch with pre-flight tenant guard
> Absorbed: `b632a4b` docs(platform#69): spec revisions — outcome-based Javadoc contract, List.copyOf, update stale 2026-06-04 partial-failure section

*message adequate — revisions polished the same spec document; title unchanged*

> **Result:** 1 commit.

---

## Group 2 — Contract test + InMemory fix + SPI Javadoc (2 → 1)
**Final message:** `fix(platform#69): storeAll() pre-flight tenant guard — mixed-tenant contract test, InMemoryMemoryStore fix, SPI Javadoc`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cfc3a4d` fix(platform#69): storeAll() pre-flight tenant guard — contract test + InMemoryMemoryStore fix | ✅ KEEP | *(see Final message above)* |
| `54d66b7` docs(platform#69): CaseMemoryStore.storeAll() — document override contract (outcome-based) | 🔽 SQUASH ↑ | *(absorbed — Javadoc follow-on, same issue, same sitting)* |

> **Result:** 1 commit.

---

## Group 3 — Mem0 storeAll feat + consistency fix (2 → 1)
**Final message:** `feat(platform#69): Mem0CaseMemoryStore.storeAll() — pre-flight tenant guard, sequential batch, List.copyOf`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `f8c68b0` feat(platform#69): Mem0CaseMemoryStore.storeAll() — pre-flight tenant guard, sequential batch, List.copyOf | ✅ KEEP | *(message adequate — original message covers the work)* |
| `3e72655` fix(platform#69): InMemoryMemoryStore.storeAll() — use List.copyOf() for consistency | 🔽 SQUASH ↑ | *(absorbed — 1-line consistency fix, code review follow-on, no new scenario)* |

> **Result:** 1 commit.

---

## AFTER — what `git log --oneline` will show

  6  commits (original)
  -3  absorbed by squash
  ──────────────────────────────────────────────
  3  commits — no content lost

Sample (most recent first):
  feat(platform#69): Mem0CaseMemoryStore.storeAll() — pre-flight tenant guard, sequential batch, List.copyOf
  fix(platform#69): storeAll() pre-flight tenant guard — mixed-tenant contract test, InMemoryMemoryStore fix, SPI Javadoc
  docs(platform#69): spec — Mem0 storeAll() sequential batch with pre-flight tenant guard
