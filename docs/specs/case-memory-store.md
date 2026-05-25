# CaseMemoryStore: Platform Design Spec

**Status:** Design reference  
**Scope:** casehub-platform — general platform capability  
**Tracking:** casehubio/platform#27  
**Consumer adoption:** casehubio/devtown#43, casehubio/clinical#33, casehubio/aml#32  
**Audience:** Platform contributors and CaseHub consumer module developers

---

## 1. What CaseMemoryStore Is (and Isn't)

### What It Is

CaseMemoryStore is a general platform capability providing queryable, permission-aware, persistent memory across CaseHub cases. It benefits ALL CaseHub consumers — devtown, clinical, aml, life, and any future consumer. It is a platform primitive, not a feature of any specific consumer module.

It sits alongside the ledger as a complementary store with a different purpose, retention model, and query interface.

### What It Is Not

- **Not a ledger replacement.** The ledger is tamper-evident, append-only, and permanent. CaseMemoryStore is queryable, mutable (facts can be updated or erased), and indefinitely retained. They serve different purposes.
- **Not an OpenClaw feature.** CaseMemoryStore has no dependency on OpenClaw. Any CaseHub consumer can use it, including those with no OpenClaw integration.
- **Not a channel context window.** The channel context window is raw message history for an active channel session — short-lived, OpenClaw-specific, and not queryable across cases.

### Three-Store Comparison

| Store | Purpose | What It Holds | Retention | Mutability | Consumer |
|-------|---------|---------------|-----------|------------|----------|
| **Ledger** | Tamper-evident record of what happened | Case events, commitments, decisions, authorisations | Permanent | Append-only | All (Level 4) |
| **CaseMemoryStore** | Queryable record of what we know | Facts, entity history, learned patterns, domain knowledge | Indefinite | Update and erase supported | All (Level 1+) |
| **ChannelContextWindow** | Active session context | Raw channel messages, recent exchange | TTL / short-term | Ephemeral | OpenClaw-specific |

### Position on the Adoption Ladder

CaseMemoryStore is orthogonal to the Level 0–4 adoption ladder. It can be added at any level from Level 1 upward. It is not a prerequisite for any level and does not depend on the ledger. A consumer at Level 1 (casehub-engine only) can adopt CaseMemoryStore without any additional platform modules.

The Memori adapter requires Postgres, which is the same infrastructure used by the JPA persistence backend. No additional infrastructure is needed to adopt CaseMemoryStore alongside the JPA backend.

---

## 2. The Problem Across All Consumers

Every CaseHub case starts cold. When a new WorkItem is created, the engine has no access to facts learned in previous cases. Everything that was observed, decided, or discovered in prior cases is invisible at case open unless a human operator manually retrieves it.

This is not an acceptable state for any of CaseHub's consumer domains.

### devtown

A new contributor files their first issue. The system has no knowledge of:
- Whether this contributor has worked on this module before
- Whether prior contributions introduced technical debt or bugs
- What the module's risk profile looks like based on historical patterns
- Whether agent capability facts from prior sessions are relevant to this issue

All of this is silently discarded between cases.

### clinical

A patient returns for a follow-up. The system has no knowledge of:
- The patient's history across prior site interactions
- Site compliance patterns that affected prior care cycles
- Agent-learned facts about medication tolerances or appointment adherence
- Flags raised in prior clinical cases for this patient

Manual record review is required. This is exactly the problem clinical knowledge management is supposed to solve.

### aml

A transaction triggers a review. The system has no knowledge of:
- The entity's history of prior transactions and SAR filings
- Patterns observed across multiple prior review cycles
- Agent findings from prior investigation cases
- Counterparty relationships established in earlier cases

Hours of manual research are required to reconstruct context that was present in prior cases.

### life

A household agent engages a contractor. The system has no knowledge of:
- Whether this contractor has been used before
- Whether prior engagements were fulfilled or resulted in disputes
- Health conditions relevant to a new medical appointment
- Property facts (boiler model, last service date) established in prior cases

The agent starts from zero every time.

---

## 3. Module Structure (casehub-platform)

CaseMemoryStore follows the same CDI SPI pattern used by `PreferenceProvider` and `CurrentPrincipal` throughout the platform. The design is consistent with the persistence backend CDI priority protocol (see `2026-05-22-persistence-backend-cdi-priority-protocol-design.md`).

### SPI in platform-api

```java
// casehub-platform-api
public interface CaseMemoryStore {

    /**
     * Store a fact associated with a case, entity, and domain.
     * Facts are permission-checked at the SPI layer before storage.
     */
    void store(MemoryFact fact);

    /**
     * Query facts relevant to a query context.
     * Permission-aware: results are filtered by the current principal's access.
     * Domain-isolated: results are filtered by the requesting agent's domain.
     */
    List<MemoryFact> query(MemoryQuery query);

    /**
     * Erase facts matching the given criteria.
     * Supports GDPR Art.17 right-to-erasure requirements.
     */
    void erase(EraseRequest request);
}
```

The SPI is queryable (not just retrievable by key), permission-aware, and supports erasure. All three properties are enforced at the SPI layer — not delegated to the backend.

### No-Op Default in platform

The default CDI bean in `casehub-platform` is a no-op implementation annotated `@DefaultBean`. When no adapter is installed:
- `store()` is a silent no-op — facts are not persisted
- `query()` returns empty — no facts are available
- `erase()` is a silent no-op

Zero overhead when no adapter is installed. No null checks in consumer code. No configuration required to opt out.

### Adapter Modules

Concrete adapters are independent modules, each providing a `@Priority`-annotated CDI bean that replaces the default:

| Module | Backend | Priority | Notes |
|--------|---------|----------|-------|
| `casehub-memory-memori` | Postgres (SQL-native) | Lowest non-default | Start here; uses existing infrastructure |
| `casehub-memory-mem0` | pgvector + BM25 | Medium | Standard vector+keyword hybrid |
| `casehub-memory-graphiti` | Neo4j / FalkorDB / Kuzu | Highest | Temporal reasoning, graph relationships |

Multiple adapters can be on the classpath; CDI priority determines which one is active. Only one adapter is active at runtime.

---

## 4. Open Source Backend Evaluation

Seven backends were evaluated. Four were ruled out. Three are recommended as adapter targets.

### Full Evaluation Table

| Project | Approach | REST API | Self-hosted | Temporal reasoning | Stars | Licence | Key limitation |
|---------|----------|----------|-------------|-------------------|-------|---------|----------------|
| **Mem0** | Vector + BM25 hybrid | Yes | Yes (Docker + pgvector) | No | 48k | Apache 2.0 | No bitemporal; requires vector infra |
| **Graphiti** | Bitemporal knowledge graph | Yes | Yes (Neo4j/FalkorDB/Kuzu) | Yes (bitemporal edges) | ~5k | Apache 2.0 | Requires graph DB; higher operational complexity |
| **Memori** | SQL-native semantic search | Yes | Yes (Postgres only) | Limited | ~2k | Apache 2.0 | Limited temporal; Postgres-only (a feature for us) |
| **Hindsight** | Event-sourced memory | No stable API | Yes | Partial | — | Apache 2.0 | Unstable API; early-stage |
| ~~**Cognee**~~ | Knowledge graph extraction | Yes | Yes | No | ~3k | Apache 2.0 | Ruled out: extraction quality inconsistent; not production-grade |
| ~~**Letta**~~ | Agent memory management | Yes | Yes | No | ~14k | Apache 2.0 | Ruled out: opinionated agent model incompatible with CaseHub worker model |
| ~~**LangChain memory**~~ | Chain-level memory buffer | No | Via LangChain | No | — | MIT | Ruled out: not a standalone service; LangChain dependency |
| ~~**GraphRAG**~~ | Community summarisation | No | Yes | No | ~22k | MIT | Ruled out: batch-oriented; not suitable for case-level real-time query |

### Recommended Adapter Strategy — CDI Priority Ladder

The recommendation is a three-tier CDI priority ladder:

**Tier 1 — Default adapter: Memori (start here)**
- SQL-native: stores and queries facts in Postgres using semantic similarity over structured SQL
- Uses existing Postgres infrastructure — no new services required
- Sub-10ms query latency (no vector index overhead for moderate corpus sizes)
- Human-readable SQL: facts are inspectable and debuggable without specialist tooling
- LoCoMo benchmark: 81.95% — strong for structured fact recall
- Apache 2.0 licence

**Tier 2 — Standard adapter: Mem0**
- Vector + BM25 hybrid recall: better performance on unstructured and fuzzy queries
- 48k GitHub stars — largest community, most production validation
- Requires Docker + pgvector (additional infra, but widely available)
- Apache 2.0 licence

**Tier 3 — Temporal adapter: Graphiti**
- Bitemporal knowledge graph: facts carry valid-from/valid-to edges; temporal queries ("what did the agent know about X at time T?") are first-class
- LongMemEval benchmark: 63–91% (range reflects task type; strong on temporal tasks)
- Requires graph database (Neo4j, FalkorDB, or Kuzu)
- Apache 2.0 licence

### Why This Strategy

**No lock-in.** The open source memory backend ecosystem is unstable. Projects in this space appear and disappear on 12–18 month cycles. The adapter pattern means CaseHub is not committed to any single backend. Swap the adapter module without changing any consumer code.

**Permission enforcement at CaseHub layer.** None of the evaluated backends understand CaseHub's `CurrentPrincipal`, `GroupMembershipProvider`, or life domain model. All of them scope memory by their own userId/sessionId. If permission enforcement were delegated to the backend, CaseHub's permission model would be unenforceable. By enforcing at the SPI layer, the backend is treated as a dumb store.

**Start simple, graduate as needed.** Memori at Tier 1 provides immediate value with zero additional infrastructure. Consumers can graduate to Mem0 when vector recall quality matters, or to Graphiti when temporal reasoning is required. The graduation is a dependency swap, not a migration.

---

## 5. Critical Design Constraints

### Permission-Aware Recall

All evaluated backends scope memory by their own userId or sessionId. None of them know:
- CaseHub's `CurrentPrincipal` and role assignments
- `GroupMembershipProvider` and group membership hierarchies
- casehub-life's domain model (health, financial, household, work)
- Multi-tenant site isolation

Permission-aware recall MUST be enforced at the SPI layer. The backend receives only the facts and queries that the SPI has already permission-checked. The backend never receives a principal identity, a role, or a domain restriction — it receives a filtered query and returns results that are then permission-filtered again before being returned to the caller.

This is non-negotiable. It is not a nice-to-have or a future enhancement.

### Domain Isolation

Domain isolation is stronger than permission-aware recall. A principal with health domain access does not grant the same access to a finance-agent operating on behalf of that principal.

The SPI enforces domain isolation as a property of the requesting agent's domain affinity, not the requesting principal's roles. A query from `finance-agent` does not return facts tagged `domain: health` even if the authenticated principal has health data access.

Domain tags are set at fact emission time and are immutable. The SPI enforces:
1. Facts stored with domain tag X cannot be queried by an agent with domain affinity Y (where Y ≠ X)
2. Facts stored with domain tag `health` cannot be queried in any context reachable by `household-junior` principals

This applies to all three adapter options. The adapter cannot relax this constraint.

### GDPR Erasure

The `erase()` method is required for GDPR Article 17 compliance. CaseMemoryStore facts about a natural person must be erasable on request, independently of ledger entries. The ledger records what happened (immutable); CaseMemoryStore records what we know about a person (erasable).

Adapters must implement `erase()` with actual deletion. Soft-delete (marking a fact as erased without removing it) is not compliant.

---

## 6. Tracking

### Implementation Issue

Primary tracking issue: **casehubio/platform#27**

Covers:
- SPI interface finalisation (`CaseMemoryStore`, `MemoryFact`, `MemoryQuery`, `EraseRequest`)
- No-op default bean implementation
- Memori adapter (Tier 1)
- Integration test scaffold

### Consumer Adoption Issues

| Consumer | Issue | Scope |
|----------|-------|-------|
| devtown | casehubio/devtown#43 | Contributor history, module risk signals, agent capability facts |
| clinical | casehubio/clinical#33 | Patient history, site compliance patterns |
| aml | casehubio/aml#32 | Entity history, prior SAR filings, counterparty relationships |
| life | (not yet raised) | Contractor history, property facts, health conditions |

### Open Questions

**Fact emission mechanism:** how do CaseHub case events cause facts to be stored in CaseMemoryStore? Three options under consideration:
1. **CDI observer:** case event bus publishes events; a CaseMemoryStore observer listens and extracts facts. Decoupled; automatic; but requires fact extraction logic to be defined per event type.
2. **Explicit API calls:** consumer code explicitly calls `CaseMemoryStore.store()` after significant events. More control; more boilerplate.
3. **OpenClaw skill:** the agent calls a CaseHub MCP tool to store a fact. Agent-driven; appropriate for agent-derived insights; not appropriate for system-observed facts.

Likely: a combination — CDI observer for system-observed facts, explicit API for consumer-specific facts, OpenClaw skill for agent-derived insights. Not yet resolved.

**Module placement:** should CaseMemoryStore live within `casehub-platform` and `casehub-platform-api`, or as a standalone top-level module (`casehub-memory`)? Arguments:
- Within platform: consistent with `PreferenceProvider`, `CurrentPrincipal` pattern; no new module to declare
- Standalone: cleaner separation; consumers can take memory without taking all of platform

Not yet resolved. Leaning toward within platform given the pattern precedent.

**Domain isolation implementation:** see life actor model spec §4 open questions. If domain isolation is structural (Option B), the SPI `query()` method must accept a `Domain` parameter. This has implications for the `MemoryQuery` type design.

**Mem0 and Graphiti adapters:** Tier 1 (Memori) is the implementation target for casehubio/platform#27. Tier 2 (Mem0) and Tier 3 (Graphiti) adapters are not yet tracked in separate issues.
