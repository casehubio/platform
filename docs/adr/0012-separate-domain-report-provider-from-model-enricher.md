# 0012 — Separate DomainReportProvider SPI from ModelEnricher

Date: 2026-09-28
Status: Accepted

## Context and Problem Statement

MCP domains need runtime health reporting (provider status, capabilities, availability) alongside the existing catalog enrichment (`ModelEnricher.state()`). The question is whether to extend `ModelEnricher` with a `report()` method or create a separate SPI.

## Decision Drivers

* Single Responsibility — catalog metadata and health monitoring require different dependencies
* Existing enrichers (`ModelRegistryEnricher`, `WorkModelEnricher`, `EngineModelEnricher`, `LedgerModelEnricher`) describe API surface metadata and lack the injected dependencies needed for runtime health assessment
* Zero migration cost — existing enrichers must not be forced to change

## Considered Options

* **Option A** — Add `default DomainReport report(String domain)` to `ModelEnricher`
* **Option B** — Separate `DomainReportProvider` SPI
* **Option C** — Keep `state()` untyped, map to `DomainReport` externally

## Decision Outcome

Chosen option: **Option B — Separate DomainReportProvider SPI**, because catalog enrichment and health monitoring are orthogonal concerns with different dependency profiles and different lifecycles.

### Positive Consequences

* A class can implement `ModelEnricher` alone (catalog), `DomainReportProvider` alone (health), or both when concerns genuinely overlap
* Zero migration cost — existing enrichers work unchanged
* Discovery uses the identical CDI pattern (`Instance<DomainReportProvider>` + `@McpDomain` matching)

### Negative Consequences / Tradeoffs

* Domains that want both catalog enrichment and health reporting implement two interfaces
* Two discovery loops in `GraphQLModelScanner` (one for enrichers, one for report providers)

## Pros and Cons of the Options

### Option A — Add report() to ModelEnricher

* Good, because single interface — domains implement one thing
* Good, because no new CDI discovery loop
* Bad, because conflates catalog description with health monitoring — existing enrichers would need injected dependencies they don't currently have
* Bad, because `ModelEnricher` grows in scope beyond its original purpose

### Option B — Separate DomainReportProvider SPI

* Good, because SRP — each interface has one clear purpose
* Good, because zero migration cost for existing enrichers
* Good, because same CDI discovery pattern — trivial to implement
* Bad, because two interfaces for domains that want both

### Option C — Keep state() untyped, map externally

* Good, because no new SPI at all
* Bad, because fragile — depends on state() map keys matching expected fields
* Bad, because no compile-time contract for report structure

## Links

* Issue #474 — structured error response type
* Issue #475 — domain report convention
* Issue #476 — platform report aggregator
* Decision D3 in `specs/issue-474-476-mcp-domain-reporting/decisions.md`
