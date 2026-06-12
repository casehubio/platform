# casehub-platform — Design

## Architecture

Zero-dependency foundational SPIs and types shared across all casehub modules. Publishes before everything else in the build order (immediately after casehub-parent BOM).

## Module Structure

| Module | Type | Purpose |
|--------|------|---------|
| `platform-api/` | Pure Java SPIs | Zero-dependency API — enums, records, interfaces |
| `platform/` | @DefaultBean impls | Configurable mocks + silent no-ops |
| `testing/` | Test fixtures | @Alternative @Priority(1) identity fixtures |
| `config/` | YAML provider | Scope-aware SmallRye Config PreferenceProvider |
| `oidc/` | OIDC integration | @RequestScoped CurrentPrincipal from SecurityIdentity |
| `expression/` | JQ evaluation | JQEvaluator for expression processing |
| `persistence-jpa/` | JPA provider | Scope-aware, hierarchy-resolved PreferenceProvider |
| `persistence-mongodb/` | MongoDB provider | @Alternative PreferenceProvider |
| `memory-inmem/` | In-memory store | @Alternative @Priority(10) volatile CaseMemoryStore |
| `memory-jpa/` | JPA store | PostgreSQL + FTS CaseMemoryStore |
| `memory-sqlite/` | SQLite store | SQLite + FTS5 CaseMemoryStore |
| `memory-mem0/` | Mem0 adapter | REST CaseMemoryStore — vector embeddings |
| `memory-graphiti/` | Graphiti adapter | Temporal knowledge graph GraphCaseMemoryStore |
| `scim/` | SCIM 2.0 | GroupMembershipProvider from SCIM directory |
| `identity/` | DID/VC | ActorDIDProvider, DIDResolver, AgentCredentialValidator |
| `agent-api/` | Agent SPI | AgentProvider + session/event types (Mutiny) |
| `agent-claude/` | Claude adapter | ClaudeAgentProvider via Claude CLI |

## Key Abstractions

- **CurrentPrincipal** — actor identity (actorId, tenancyId, roles/groups)
- **PreferenceProvider** — scope-aware configuration hierarchy
- **CaseMemoryStore** — blocking SPI for case memory (store, query, erase)
- **GraphCaseMemoryStore** — graph-native extension with temporal queries
- **GroupMembershipProvider** — group membership resolution
- **AgentProvider** — agent session lifecycle (Mutiny)

## SPI Contracts

Every SPI in `platform-api` gets a `@DefaultBean` implementation in `platform/`:
- **Configurable mock** pattern: returns values driven by `@ConfigProperty`
- **Silent no-op** pattern: always returns empty/void

Real implementations live in dedicated modules and displace defaults by classpath presence or `@Alternative @Priority`.

## Data Model

_See individual module Flyway migrations for schema details._

## Configuration

Prefix: `casehub.` — see individual module documentation for specific properties.
