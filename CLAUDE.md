# casehub-platform Workspace

**Name:** casehub-platform

**Physical path:** `/Users/mdproctor/claude/casehub/platform/CLAUDE.md`
**Symlinked at:** `/Users/mdproctor/claude/public/casehub/platform/CLAUDE.md`
**Project repo:** `/Users/mdproctor/claude/casehub/platform`
**Workspace:** `/Users/mdproctor/claude/public/casehub/platform`
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/platform` and `add-dir /Users/mdproctor/claude/public/casehub/platform` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| adr | `adr/` |
| write-blog | `blog/` |

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/platform`) — plans, blog, specs, snapshots, handover
- **Project repo** (`/Users/mdproctor/claude/casehub/platform`) — source code, ADRs

Never rely on CWD for git operations:
```bash
git -C /Users/mdproctor/claude/public/casehub/platform ...   # workspace artifacts
git -C /Users/mdproctor/claude/casehub/platform ...          # project artifacts
```

Two remotes are configured on the project repo:
- `origin` → `casehubio/platform` (canonical)
- `mdproctor` → `mdproctor/platform` (personal fork)

**Git hooks:** `.githooks/pre-push` is committed. Activate on each clone:
```bash
git config core.hooksPath .githooks
```

Push to both after squash or significant merges:
```bash
git push --force-with-lease origin main
git push --force mdproctor main   # --force on first push after fork creation
```

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `adr/` |
| protocols  | garden      | `/Users/mdproctor/claude/casehub/garden/docs/protocols/` — never create local protocol files |
| specs      | project     | lands in `docs/` |
| blog       | workspace   | staged; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | |
| snapshots  | workspace   | |
| handover   | workspace   | |

**Blog directory:** `/Users/mdproctor/claude/public/casehub/platform/blog/`

## Rules

- `platform-api/` must remain zero-dependency — no Quarkus, no JPA, no casehubio imports. Pure Java only.
- `platform/` contains Quarkus @DefaultBean implementations only — no domain logic
- Every SPI in platform-api gets a @DefaultBean implementation in platform
- Two @DefaultBean patterns exist:
  - **Configurable mock** (PreferenceProvider, CurrentPrincipal): returns values driven by @ConfigProperty — suitable when tests need to set specific return values
  - **Silent no-op** (CaseMemoryStore): always returns empty/void — suitable when the capability is optional and "not installed" means "nothing happens"
- `config/` reads YAML preference files at startup — declare as compile scope in production; not needed on test-only classpaths (mock handles test defaults via `application.properties`)

## Project Type

type: java

## Repository Role

Zero-dependency foundational SPIs and types shared across all casehub modules. Publishes before everything else in the build order.

**Build order position:** immediately after casehub-parent BOM. No casehubio dependencies.

**Consumers (do not modify these repos — raise issues instead):**
- casehub-ledger, casehub-work, casehub-qhorus, casehub-engine, claudony, devtown, aml, clinical

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode deploy -DskipTests   # CI only — requires GITHUB_TOKEN
```

## Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `platform-api/` | `casehub-platform-api` | Pure Java SPIs — zero deps |
| `platform/` | `casehub-platform` | Quarkus @DefaultBean implementations (configurable mocks + no-ops) + `ReactiveCaseMemoryStore` interface + `BlockingToReactiveBridge` |
| `testing/` | `casehub-platform-testing` | @Alternative @Priority(1) identity fixtures — no Quarkus runtime |
| `config/` | `casehub-platform-config` | Scope-aware YAML + SmallRye Config PreferenceProvider — displaces mock when on classpath |
| `oidc/` | `casehub-platform-oidc` | @RequestScoped OIDC-backed CurrentPrincipal — reads actorId/groups from SecurityIdentity |
| `expression/` | `casehub-platform-expression` | JQ expression evaluation (JQEvaluator) |
| `persistence-jpa/` | `casehub-platform-persistence-jpa` | JPA-backed PreferenceProvider — scope-aware, hierarchy-resolved, current-only. Add as compile dep; consumers must add `classpath:db/platform/migration` to Flyway locations |
| `persistence-mongodb/` | `casehub-platform-persistence-mongodb` | MongoDB-backed PreferenceProvider — @Alternative @Priority(1), beats JPA when co-deployed. No Flyway; startup bean creates scope index |
| `memory-inmem/` | `casehub-platform-memory-inmem` | @Alternative @Priority(10) volatile CaseMemoryStore — ConcurrentHashMap, constructor-injected CurrentPrincipal, no quarkus:build goal. Add as test scope for @QuarkusTest isolation; compile for ephemeral installs. Do NOT combine with memory-jpa or memory-sqlite in the same scope |
| `memory-jpa/` | `casehub-platform-memory-jpa` | @ApplicationScoped JPA CaseMemoryStore — PostgreSQL, Flyway V1000 (`classpath:db/memory/migration`), FTS via websearch_to_tsquery when question provided. No quarkus:build goal (CurrentPrincipal only on test classpath). Use @TestTransaction not @Transactional in tests |
| `memory-sqlite/` | `casehub-platform-memory-sqlite` | @Alternative @Priority(1) SQLite CaseMemoryStore — xerial JDBC + HikariCP (WAL mode) + FTS5 + Flyway programmatic. Configure `casehub.memory.sqlite.path`. No quarkus:build goal. Do NOT combine with memory-inmem or memory-jpa in the same scope |
| `memory-mem0/` | `casehub-platform-memory-mem0` | @Alternative @Priority(1) Mem0 REST CaseMemoryStore — vector embeddings via Mem0 OSS (Docker + pgvector), infer:false (verbatim storage). Tenant isolation via compound `user_id={tenantId}::{entityId}` (Mem0 OSS has no app_id). GET /memories unbounded; limit client-side. RELEVANCE uses POST /search with top_k + threshold. Configure: `quarkus.rest-client.mem0.url`, `casehub.memory.mem0.api-key`. No quarkus:build goal. Do NOT combine with memory-inmem or memory-sqlite in same scope |
| `memory-graphiti/` | `casehub-platform-memory-graphiti` | @Alternative @Priority(2) Graphiti REST GraphCaseMemoryStore — temporal knowledge graph (Neo4j/FalkorDB/Kuzu via Graphiti OSS). LLM entity extraction (async). group_id={tenantId}::{entityId}::{domain} (domain is the partition key — entity relationships span cases within a domain). ERASE_DOMAIN_CASE: domain-level deletion via DELETE /group (cascading, complete); case-level via DELETE /episode (best-effort, EpisodicNode only). ERASE_ENTITY: requires `casehub.memory.graphiti.known-domains` (comma-separated domain list). RELEVANCE/graphQuery() → POST /search per entity; CHRONOLOGICAL → GET /episodes/{group_id} per entity. Configure: `quarkus.rest-client.graphiti.url`, `casehub.memory.graphiti.api-key`. No Flyway. Do NOT combine with other @Priority(2) adapters |
| `endpoints-memory/` | `casehub-platform-endpoints-memory` | @Alternative @Priority(100) volatile InMemoryEndpointRegistry — ConcurrentHashMap, tenant-filtered, platform-global visibility. Tier 4 CDI priority (beats JPA and NoSQL adapters). Data lost on restart. Add test scope for @QuarkusTest isolation; compile scope for ephemeral installs. Do NOT combine with a JPA endpoints backend in same scope |
| `scim/` | `casehub-platform-scim` | @ApplicationScoped SCIM 2.0 GroupMembershipProvider — displaces @DefaultBean mock. Auth: casehub.platform.scim.token (static) or quarkus.oidc-client.scim.* (client-credentials). @CacheResult on membersOf(). Pagination: casehub.platform.scim.member-page-size (default 1000). No quarkus:build goal |
| `identity/` | `casehub-platform-identity` | @Alternative ActorDIDProvider/DIDResolver/AgentCredentialValidator impls — did:key, did:web, SCIM2, config-based. SPIs and model types in platform-api. Config prefix: casehub.identity.* |
| `agent-api/` | `casehub-platform-agent-api` | AgentProvider SPI + AgentSessionConfig, AgentEvent, AgentMcpServer, typed exceptions (AgentProcessException, AgentSessionLimitException, AgentTimeoutException). Mutiny only — no Quarkus. Package: `io.casehub.platform.agent` |
| `agent-claude/` | `casehub-platform-agent-claude` | `ClaudeAgentProvider @ApplicationScoped` + `ClaudeAgentClient @Startup` — activates by classpath presence, requires Claude CLI. Concurrent-session semaphore enforces `AgentSessionConfig.maxConcurrentSessions`. Scheduled subprocess closure for wall-clock timeout. Package: `io.casehub.platform.agent.claude` |

## Package Structure (platform-api)

```
io.casehub.platform.api
  .actor         — ActorStateContributor (SPI: contribute data to a unified actor state view, @ApplicationScoped),
                   ActorStateAccumulator (visitor: trustScore, capabilityScore — assembled concurrently by aggregator)
  .path          — Path, hierarchical scope/label paths
  .preferences   — PreferenceProvider, Preferences, PreferenceKey<T> (carries defaultValue + parser),
                   SettingsScope, MapPreferences, Preference, SingleValuePreference, MultiValuePreference
  .identity      — CurrentPrincipal, GroupMembershipProvider,
                   ActorDIDProvider (SPI: didFor(actorId) → Optional<String>),
                   DIDResolver (SPI: resolve(did) → Optional<DIDDocument>),
                   AgentCredentialValidator (SPI: validate(actorId, did) → Optional<CredentialValidationResult>),
                   DIDDocument (record: id, verificationMethods, alsoKnownAs),
                   VerificationMethod (record: id, type, publicKeyBytes — defensive copy),
                   IdentityVerificationResult (VALID | UNVERIFIABLE | UNSIGNED | DID_UNRESOLVABLE | IDENTITY_MISMATCH | KEY_MISMATCH),
                   CredentialValidationResult (VALID | EXPIRED | INVALID_SIGNATURE | ISSUER_UNKNOWN | NOT_FOUND),
                   IdentityBindingStatus (VALID | UNSIGNED | DID_UNRESOLVABLE | IDENTITY_MISMATCH | KEY_MISMATCH | CREDENTIAL_EXPIRED | CREDENTIAL_INVALID),
                   AgentIdentityValidatedEvent (CDI event record: VALID binding),
                   AgentIdentityViolationEvent (CDI event record: non-VALID binding)
  .endpoints     — EndpointRegistry (SPI: register/resolve/discover/deregister by (Path, tenancyId)),
                   EndpointDescriptor (record: path, tenancyId, type, protocol, properties, credentialRef, capabilities),
                   EndpointPermissions (static: assertTenant(tenancyId, principal) — write-auth for runtime registration),
                   EndpointType (enum: SYSTEM/SERVICE/WORKER/AGENT),
                   EndpointProtocol (enum: HTTP/GRPC/KAFKA/MCP/CAMEL/QHORUS),
                   EndpointCapability (enum: SEND/RECEIVE/QUERY/DISPATCH),
                   EndpointQuery (record: tenancyId, type, protocol, requiredCapabilities),
                   EndpointPropertyKeys (reserved cross-protocol property keys: URL, TOPIC)
  .memory        — CaseMemoryStore (blocking SPI) + GraphCaseMemoryStore (graph-native extension: graphQuery(GraphMemoryQuery)),
                   MemoryCapability (enum: declared adapter capabilities), MemoryCapabilityException,
                   MemoryResultType (DEFAULT/FACTS), GraphMemoryQuery (graph-native query: tenantId, entityIds, domain,
                   question, limit, since, validAt, entityTypes, resultType),
                   MemoryDomain, MemoryInput, Memory,
                   MemoryQuery (entityIds: List<String>, MemoryOrder, with* fluent API),
                   EraseRequest, MemoryPermissions (static tenant assertion utility),
                   MemoryOrder (enum: CHRONOLOGICAL / RELEVANCE),
                   MemoryAttributeKeys (reserved cross-domain keys + confidence helpers + VALID_FROM/VALID_UNTIL)
  .actor         — ActorStateContributor (SPI: contribute actor workload data to an ActorStateAccumulator),
                   ActorStateAccumulator (visitor passed to each contributor to accumulate active-cases,
                   open-WorkItems, and open-obligation slices of the actor state view)
```

`platform/` also exposes `ReactiveCaseMemoryStore` (Mutiny SPI) and `BlockingToReactiveBridge @DefaultBean`
at `io.casehub.platform.memory`.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/platform
**Changelog:** GitHub Releases
