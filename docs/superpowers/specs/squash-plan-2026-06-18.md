# Squash Plan — issue-98-cloudEvent-streams (2026-06-18)

Range: `mdproctor/main..HEAD` — 26 commits → 11 commits

---

## Summary

- Group 1: Spec design (13→1): CloudEvent + stream modules design spec
- Group 2: Build/version management (1 KEEP)
- Group 3: platform-api (2→1): EndpointRegistered, AMQP, STREAM_EVENT_TYPE
- Group 4: endpoints-memory (2→1): InMemoryEndpointRegistry EndpointRegistered firing
- Group 5: streams-kafka (1 KEEP)
- Group 6: streams-amqp (1 KEEP)
- Group 7: streams-webhook (1 KEEP)
- Group 8: streams-poll (1 KEEP)
- Group 9: streams-camel (2→1)
- Group 10: Documentation (1 KEEP)
- Group 11: Code review fixes (3→1 MERGE)

---

## AFTER — what git log --oneline will show

```
  26  commits (original)
  -15  absorbed by squash/merge
  ──────────────────────────────
  11  commits — no content lost

  fix(platform#98): code review — kafka Javadoc/config, poll fetchBytes, webhook @Startup + URL note
  docs(platform#98): update CLAUDE.md and ARC42STORIES for stream modules and endpoint additions
  feat(platform#98): streams-camel — dynamic Camel route builder via EndpointRegistered
  feat(platform#98): streams-poll — scheduled HTTP GET poller with explicit status code check
  feat(platform#98): streams-webhook — CloudEvents HTTP binding receiver
  feat(platform#98): streams-amqp — classpath-activated AMQP stream ingestion
  feat(platform#98): streams-kafka — classpath-activated Kafka stream ingestion
  feat(platform#98): InMemoryEndpointRegistry fires EndpointRegistered via constructor-injected Event<>
  feat(platform#98): add EndpointRegistered, EndpointProtocol.AMQP, STREAM_EVENT_TYPE; cloudevents-core compile dep
  build(platform#98): add cloudevents-core:4.0.1 to version management, declare 5 stream modules
  docs(platform#98): CloudEvent + stream modules design spec
```

---

OLD PLAN (superseded):

---

## Group 1 — Spec brainstorming iterations (7 → 1)

**Final message:** `docs(platform#70,#90,#99): design spec — parallel storeAll, ReactiveCaseMemoryStore move, cross-tenant erasure`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `a4acbc4` docs(platform#70,#90,#99): brainstorming spec | ✅ KEEP | *(see Final message above)* |
| `21d210b` docs(platform#70,#90,#99): revise spec after code review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `c44ec2f` docs(platform#70,#90,#99): revise spec after round 2 review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `7803267` docs(platform#70,#90,#99): revise spec after round 3 review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `16b3614` docs(platform#70,#90,#99): revise spec after round 4 review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `c3f2384` docs(platform#70,#90,#99): revise spec after round 5 review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `f507874` docs(platform#70,#90,#99): revise spec after round 6 review | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |

> **Result:** 1 commit.

---

## Group 2 — fix(platform#70): assertTenant pre-flight (1 → 1)

✅ KEEP `c4d38be` fix(platform#70): 3-arg assertTenant pre-flight in Mem0 and SQLite storeAll()

> **Result:** 1 commit (no action).

---

## Group 3 — feat(platform#70): parallel storeAll (2 → 1)

| Commit | Action | Curated result |
|--------|--------|----------------|
| `89aae52` feat(platform#70): bounded-parallel storeAll in Mem0 — Semaphore(cap) + Uni.join().andFailFast() | ✅ KEEP | *(message adequate — unchanged)* |
| `0f2955a` test(platform#70): fix test name, remove redundant import and duplicate empty test | 🔽 SQUASH ↑ | *(absorbed — test cleanup for same commit)* |

> **Result:** 1 commit.

---

## Group 4 — feat(platform#90): ReactiveCaseMemoryStore move (3 → 1)

| Commit | Action | Curated result |
|--------|--------|----------------|
| `d99c5ba` feat(platform#90): move ReactiveCaseMemoryStore to platform-api; fix UnsupportedOperationException → MemoryCapabilityException | ✅ KEEP | *(message adequate — unchanged)* |
| `7068bdd` refactor(platform#90): update casehub-platform imports for ReactiveCaseMemoryStore move | 🔽 SQUASH ↑ | *(absorbed — mechanical follow-on to the move)* |
| `0c06ffd` docs(platform#90): update CLAUDE.md — ReactiveCaseMemoryStore now in platform-api | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; no standalone value)* |

> **Result:** 1 commit.

---

## Group 5 — feat(platform#99): eraseEntityAcrossTenants (9 → 1)

**Final message:** `feat(platform#99): eraseEntityAcrossTenants — GDPR Art.17 cross-tenant erasure across all six adapters`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `66bf40b` feat(platform#99): CROSS_TENANT_ERASE capability + assertCrossTenantAdmin + CaseMemoryStore.eraseEntityAcrossTenants | ✅ KEEP | *(see Final message above)* |
| `f2f5696` feat(platform#99): eraseEntityAcrossTenants on ReactiveCaseMemoryStore + BlockingToReactiveBridge | 🔽 SQUASH ↑ | *(absorbed — same capability, same issue)* |
| `28b3e88` feat(platform#99): NoOpCaseMemoryStore.eraseEntityAcrossTenants() no-op override | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `c043a8a` feat(platform#99): InMemoryMemoryStore.eraseEntityAcrossTenants + CROSS_TENANT_ERASE capability | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `0b198ea` feat(platform#99): JpaMemoryStore.eraseEntityAcrossTenants — single DELETE IN query | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `8c52ba8` feat(platform#99): SqliteMemoryStore.eraseEntityAcrossTenants — chunked DELETE IN (SQLITE_IN_CHUNK=500) | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `d0bfe9c` feat(platform#99): Mem0CaseMemoryStore.eraseEntityAcrossTenants — sequential loop, retry-is-safe | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `61ac56d` feat(platform#99): GraphitiCaseMemoryStore.eraseEntityAcrossTenants — known-domains × tenantIds loop | 🔽 SQUASH ↑ | *(absorbed — adapter implementation)* |
| `3fd874c` test(platform#99): contract test security boundary — eraseEntityAcrossTenants requires cross-tenant admin | 🔽 SQUASH ↑ | *(absorbed — test for same feature)* |

> **Result:** 1 commit.

---

## Group 6 — docs(platform#70,#90,#99): ARC42STORIES (1 → 1)

✅ KEEP `775de88` docs(platform#70,#90,#99): ARC42STORIES — retire §12 risk #70, add §8 L6 chapter, extend §13 glossary

> **Result:** 1 commit (no action).

---

## AFTER — what `git log --oneline` will show

```
  23  commits (original)
  -0   pruned by filter-repo
  -17  absorbed by squash
  ──────────────────────────────────────────────
   6  commits — no content lost
```

Expected order (most recent first):
```
<sha>  docs(platform#70,#90,#99): ARC42STORIES — retire §12 risk #70, add §8 L6 chapter, extend §13 glossary
<sha>  feat(platform#99): eraseEntityAcrossTenants — GDPR Art.17 cross-tenant erasure across all six adapters
<sha>  feat(platform#90): move ReactiveCaseMemoryStore to platform-api; fix UnsupportedOperationException → MemoryCapabilityException
<sha>  feat(platform#70): bounded-parallel storeAll in Mem0 — Semaphore(cap) + Uni.join().andFailFast()
<sha>  fix(platform#70): 3-arg assertTenant pre-flight in Mem0 and SQLite storeAll()
<sha>  docs(platform#70,#90,#99): design spec — parallel storeAll, ReactiveCaseMemoryStore move, cross-tenant erasure
```
