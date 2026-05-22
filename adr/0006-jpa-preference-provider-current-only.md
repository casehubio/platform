# 0006 — JPA PreferenceProvider is current-only — effectiveAt ignored

Date: 2026-05-22
Status: Accepted

## Context and Problem Statement

`SettingsScope` carries an `effectiveAt` Instant alongside the scope `Path`. The contract
allows implementations to resolve preferences as they stood at an arbitrary past time.
The `config/` and mock implementations already ignore `effectiveAt`. The JPA provider is
the first with a real store — the decision of whether to honour it now becomes consequential.

## Decision Drivers

* No caller currently passes a non-current `effectiveAt` — all use `SettingsScope.of(path)` = `Instant.now()`
* Time-travel reads require a write model that records when each value became effective
* The preferences-editor (issue #8) hasn't been designed; adding `effective_from` to the schema now means designing a versioned write path that doesn't yet exist
* Audit concerns are better served by recording resolved preference values in ledger entries at decision time, not by querying the preference store retroactively

## Considered Options

* **Option A** — Honour `effectiveAt`: add `effective_from TIMESTAMP` to `platform_preference`, query `WHERE effective_from <= :effectiveAt`
* **Option B** — Current-only: ignore `effectiveAt`, treat all rows as current
* **Option C** — Defer with guard: raise `UnsupportedOperationException` if `effectiveAt` differs materially from now

## Decision Outcome

Chosen option: **Option B**, because no caller uses `effectiveAt` today and the write
model needed to make it meaningful doesn't exist. Adding the column without the write
path creates schema debt without delivering capability.

### Positive Consequences

* Schema stays minimal — no orphaned `effective_from` column with no write path
* Simpler query (no temporal predicate, no range overlap logic)

### Negative Consequences / Tradeoffs

* Time-travel queries require a schema migration and write-path redesign when needed
* The `PreferenceProvider` contract implies temporal capability that this implementation silently ignores — callers passing non-current `effectiveAt` receive current data without error

## Links

* casehubio/platform#6 — persistence-jpa implementation
* casehubio/platform#8 — preferences-editor (the write path that would make `effectiveAt` meaningful)
