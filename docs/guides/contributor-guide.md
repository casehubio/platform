# casehub-platform — Contributor Guide

> Internal architecture, module structure, and extension points for platform developers.

**Repo:** [`casehubio/platform`](https://github.com/casehubio/platform)

---

## Module Structure

### The Three-Layer Model

```
platform-api/               <- Tier 1: zero dependencies — pure Java interfaces and records
platform/                   <- Tier 3: Quarkus @DefaultBean mocks, @ConfigProperty
testing/                    <- companion: @Alternative @Priority(1) test fixtures (CDI API only)
```

`platform-api/` must never import Quarkus, CDI, JPA, or any casehubio artifact. This constraint is what makes the SPIs useful to every module in the stack.

### Full Module Listing

| Module | Artifact | CDI | Purpose |
|--------|----------|-----|---------|
| `platform-api/` | `casehub-platform-api` | (none) | Pure Java SPIs — zero deps |
| `platform/` | `casehub-platform` | `@DefaultBean` | Quarkus mocks + no-ops; `DataSourceRouter`; `CloudEventTypeDispatcher` |
| `testing/` | `casehub-platform-testing` | `@Alternative @Priority(1)` | `FixedCurrentPrincipal` (@Priority(200)), `InMemoryGroupMembershipProvider` |
| `config/` | `casehub-platform-config` | `@ApplicationScoped` | Scope-aware YAML + SmallRye Config |
| `oidc/` | `casehub-platform-oidc` | `@Alternative @Priority(100) @RequestScoped` | OIDC-backed `CurrentPrincipal` |
| `expression/` | `casehub-platform-expression` | `@ApplicationScoped` | JQ + MVEL3 expression engines; `DefaultExpressionEngineRegistry` |
| `persistence-jpa/` | `casehub-platform-persistence-jpa` | `@ApplicationScoped` | JPA preference store — Flyway V2, scope-aware hierarchy |
| `persistence-mongodb/` | `casehub-platform-persistence-mongodb` | `@Alternative @Priority(1)` | MongoDB preference backend — beats JPA when co-deployed |
| `datasource-alpha/` | `casehub-platform-datasource-alpha` | (library) | Rete alpha network — `AlphaDataSource`, TypeNode, FilterNode, FanOutProcessor |
| `datasource-inmem/` | `casehub-platform-datasource-inmem` | `@Alternative @Priority(100)` | In-memory `DataSourceRegistry` — ConcurrentHashMap, self-pruning |
| `datasource-jpa/` | `casehub-platform-datasource-jpa` | `@ApplicationScoped` | JPA `DataSourceRegistry` — startup reconciliation, `@Transactional` |
| `identity/` | `casehub-platform-identity` | `@ApplicationScoped` | DID resolution (did:key, did:web, SCIM), actor-to-DID mapping, VC validation |
| `acl-inmem/` | `casehub-platform-acl-inmem` | `@Alternative @Priority(10)` | In-memory ACL — ConcurrentHashMap, group-based grants, parent-child hierarchy |
| `acl-jpa/` | `casehub-platform-acl-jpa` | `@ApplicationScoped` | JPA ACL — Hibernate ORM Panache, audit logging, deny entries, retention purge |
| `acl-admin/` | `casehub-platform-acl-admin` | `@ApplicationScoped` | REST API for ACL admin — `@RunOnVirtualThread`, `@RolesAllowed("admin")` |
| `governance/` | `casehub-platform-governance` | `@ApplicationScoped` | `DefaultPolicyEnforcer` — retry/timeout/backoff on virtual thread executor |
| `credentials-quarkus/` | `casehub-platform-credentials-quarkus` | `@Alternative @Priority(1)` | Bridge `CredentialResolver` to Quarkus `CredentialsProvider` |
| `scim/` | `casehub-platform-scim` | `@ApplicationScoped` | SCIM 2.0 `GroupMembershipProvider` |
| `agent-api/` | `casehub-platform-agent-api` | (library) | `AgentProvider` SPI — single-shot + multi-turn; Mutiny only |
| `agent-claude/` | `casehub-platform-agent-claude` | `@Alternative @Priority(10)` | Claude CLI subprocess — semaphore-gated, wall-clock timeout |
| `agent-langchain4j/` | `casehub-platform-agent-langchain4j` | `@Alternative @Priority(1)` | Bidirectional LangChain4j interop |
| `endpoints-memory/` | `casehub-platform-endpoints-memory` | `@Alternative @Priority(100)` | In-memory `EndpointRegistry` — volatile, Tier 4 CDI |
| `endpoints-config/` | `casehub-platform-endpoints-config` | `@Startup @ApplicationScoped` | YAML endpoint populator — `${VAR}` interpolation, multi-file |
| `notifications/` | `casehub-platform-notifications` | `@ApplicationScoped` | REST + SSE — list, mark-read, dismiss, unread-count, preferences, suppression |
| `notifications-inmem/` | `casehub-platform-notifications-inmem` | `@Alternative @Priority(100)` | In-memory `NotificationStore` — bounded eviction, cursor pagination |
| `notifications-jpa/` | `casehub-platform-notifications-jpa` | `@ApplicationScoped` | JPA `NotificationStore` — Hibernate Reactive Panache, keyset pagination, retention scheduler |
| `notification-dispatch/` | `casehub-platform-notification-dispatch` | `@ApplicationScoped` | Three-path delivery: digest/suppress/immediate; `DigestFlushScheduler`; `DeliveryRetryProcessor` |
| `notification-settings-inmem/` | `casehub-platform-notification-settings-inmem` | `@Alternative @Priority(100)` | In-memory preference/suppression store |
| `notification-settings-jpa/` | `casehub-platform-notification-settings-jpa` | `@ApplicationScoped` | JPA preference/suppression store — JSON TEXT columns, retention scheduler |
| `delivery-channel-inmem/` | `casehub-platform-delivery-channel-inmem` | `@ApplicationScoped` | Channel-to-deliverer registry — **production implementation** (channels are static) |
| `delivery-tracking-inmem/` | `casehub-platform-delivery-tracking-inmem` | `@Alternative @Priority(100)` | In-memory `DeliveryAttemptStore` |
| `delivery-tracking-jpa/` | `casehub-platform-delivery-tracking-jpa` | `@ApplicationScoped` | JPA `DeliveryAttemptStore` — `SKIP LOCKED` claims, retention purge |
| `digest-inmem/` | `casehub-platform-digest-inmem` | `@Alternative @Priority(100)` | In-memory `DigestBuffer` |
| `digest-jpa/` | `casehub-platform-digest-jpa` | `@ApplicationScoped` | JPA `DigestBuffer` — drain via SELECT+DELETE in transaction |
| `subscriptions/` | `casehub-platform-subscriptions` | `@ApplicationScoped` | Subscription matching engine + REST — alpha network wiring, expression compilation |
| `subscriptions-inmem/` | `casehub-platform-subscriptions-inmem` | `@Alternative @Priority(100)` | In-memory `SubscriptionStore` — scope-aware, CDI events |
| `subscriptions-jpa/` | `casehub-platform-subscriptions-jpa` | `@ApplicationScoped` | JPA `SubscriptionStore` — OR-disjunction scope queries, JSON TEXT columns |
| `streams-kafka/` | `casehub-platform-streams-kafka` | `@Startup` | Kafka connector — static `@Incoming`, CloudEvent builder |
| `streams-amqp/` | `casehub-platform-streams-amqp` | `@Startup` | AMQP connector — single address per channel |
| `streams-webhook/` | `casehub-platform-streams-webhook` | `@Startup` | Webhook receiver — structured CloudEvents HTTP binding |
| `streams-poll/` | `casehub-platform-streams-poll` | `@Startup` | HTTP GET poller — `@Scheduled`, per-endpoint failure isolation |
| `streams-camel/` | `casehub-platform-streams-camel` | `@ApplicationScoped` | Camel dynamic routes — the only connector with runtime route addition |
| `preferences-editor/` | `casehub-platform-preferences-editor` | `@ApplicationScoped` | REST API for preference writes + schema discovery |
| `platform-view/` | `casehub-platform-view` | `@ApplicationScoped` | `SubjectViewEvaluator` + `SubjectViewOrchestrator` — label-path view evaluation |
| `platform-view-inmem/` | `casehub-platform-view-inmem` | `@Alternative @Priority(100)` | In-memory view store + membership tracker |
| `platform-view-jpa/` | `casehub-platform-view-jpa` | `@ApplicationScoped` | JPA view store — `JpaLabelPatternQuerySupport` for domain consumers |

**Removed from build:** `memory-inmem/`, `memory-jpa/`, `memory-sqlite/`, `memory-mem0/`, `memory-graphiti/` — memory backends migrated to casehub-neocortex (neocortex#56). Directories remain on disk.

---

## Internal Architecture

### @DefaultBean Displacement Pattern

Every SPI in `platform-api/` gets a `@DefaultBean` implementation in `platform/`. When a real implementation (e.g. `casehub-platform-oidc`) is on the classpath, CDI displaces the mock automatically. No exclusion config needed.

Two patterns exist:

| Pattern | Used by | Behaviour |
|---------|---------|-----------|
| **Configurable mock** | `PreferenceProvider`, `CurrentPrincipal` | Returns `@ConfigProperty` values — tests set specific returns |
| **Silent no-op** | `CaseMemoryStore`, `AgentProvider` | Returns empty/void — system works without the capability |

### CDI Priority Ladder

All in-memory/JPA module pairs follow the same convention:

| CDI annotation | Meaning |
|----------------|---------|
| `@DefaultBean` | Yields to anything — mock/no-op |
| `@Alternative @Priority(1)` | Low-priority real implementation |
| `@Alternative @Priority(10)` | Standard real implementation |
| `@Alternative @Priority(100)` | In-memory / test doubles |
| `@ApplicationScoped` (no Alternative) | Production JPA implementations |

### Alpha Network (Rete Pattern)

`AlphaDataSource<T>` implements the Rete algorithm's alpha network:

```
add(object)
  |---> directSubscribers (FanOutProcessor) — all objects, no filter
  +---> typeNodes (ConcurrentHashMap<Object, TypeNode>)
          +---> TypeNode: checks objectType.matches()
                |---> noFilterSubscribers (FanOutProcessor) — type match only
                +---> filterNodes (List<FilterNode>)
                        +---> FilterNode: checks predicate.test()
                              +---> fanOut (FanOutProcessor) — type + filter match
```

Node sharing by `getTypeKey()` (type nodes) and `FilterExpression` matching (filter nodes). Self-pruning: empty TypeNodes removed when last subscriber unsubscribes. Error isolation: exceptions are WARN-logged, never propagate to other subscribers.

### Self-Pruning Deregistration Lifecycle

1. `registry.deregister()` calls `source.markForRemoval(cleanupCallback)`
2. If `shareCount == 0`, cleanup fires immediately
3. Otherwise the DataSource enters "pending removal" — continues accepting `add()` and subscriptions
4. `DataSourceDeregistered` fires via `fireAsync()` — observers call `handle.unsubscribe()`
5. Each `unsubscribe()` decrements `shareCount` — last subscriber triggers cleanup
6. Cleanup uses `sources.remove(key, source)` (identity-based) — prevents corruption if a replacement was registered during drain
7. Re-registration during drain creates a new `AlphaDataSource`

### DID Resolution — Composite Pattern

`CompositeDIDResolver` iterates all `@DIDMethod`-qualified resolvers by `@Priority`, returning the first non-empty result.

| Resolver | `@Priority` | Method | Notes |
|----------|-------------|--------|-------|
| `KeyDIDResolver` | 100 | `did:key:` | Ed25519 + P-256 + secp256k1 (manual ASN.1 — JDK 15+ removed secp256k1) |
| `WebDIDResolver` | 100 | `did:web:` | HTTPS GET with SSRF protection (rejects RFC 1918, loopback, link-local) |
| `ScimDIDResolver` | 1000 | (any) | Synthetic DID documents from SCIM2 x509Certificates |

Same composite pattern for `ActorDIDProvider` — `ConfiguredActorDIDProvider` (@Priority 100) + `ScimActorDIDProvider` (@Priority 200).

### Notification Data Flow

1. Domain modules produce `SubscribableEvent` into the notification DataSource (`casehub/platform/notifications`)
2. `SubscriptionEngine` evaluates against alpha network, fires `SubscriptionMatched`
3. `NotificationDispatcher` resolves targets, applies template, checks suppression, routes to channels
4. `NotificationStore` persists, fires `NotificationCreated`
5. REST + SSE endpoints expose to clients
6. `DeliveryChannelRegistry` maps channels to `NotificationDeliverer` implementations
7. Delivery tracked by `DeliveryAttemptStore`; digests buffered by `DigestBuffer`

### Expression Engines

| Engine | Type Key | Backend | Context Type |
|--------|----------|---------|-------------|
| `JQExpressionEngine` | `"jq"` | jackson-jq 1.6 | `JsonNode` |
| `MvelExpressionEngine` | `"mvel"` | MVEL3 3.0.0-SNAPSHOT | `Map<String, Object>` |

`DefaultExpressionEngineRegistry` discovers all `ExpressionEngine` beans at `@PostConstruct`. The subscription engine uses this to compile filter expressions into `FilterExpression<T>` predicates for alpha network routing.

### Agent Infrastructure

`AgentProvider` SPI with two execution paths:
- `invoke()` — single-shot, per-invocation semaphore
- `openSession()` — multi-turn `AgentSession` (IDLE/ACTIVE/CLOSED state machine), semaphore held for session lifetime

`agent-claude/` wraps the Claude Code CLI as a subprocess. `agent-langchain4j/` provides bidirectional interop: any `ChatModel` as `AgentProvider`, any `AgentProvider` as `ChatModel`. These are not interchangeable — `agent-claude/` runs an autonomous agent; LangChain4j runs a chat completion with caller-managed tool loop.

`AgentEvent` is sealed with variants: `TextDelta`, `ThinkingDelta`, `ToolCallDelta`, `ToolCallComplete`, `ToolResult`, `InvocationComplete` (terminal with cost/usage/timing metadata).

---

## Dependencies

### Depends On

- `casehub-parent` (BOM only) — no casehubio runtime dependencies
- Quarkus (in implementation modules only, never in `platform-api/`)
- jackson-jq 1.6 (expression module)
- MVEL3 3.0.0-SNAPSHOT from JBoss Nexus snapshots (expression module)
- Spring AI Community `claude-code-sdk` (agent-claude module)

### Depended On By

Every casehub module depends on `platform-api`. Known consumers:
- casehub-ledger, casehub-work, casehub-qhorus, casehub-engine
- claudony, devtown, aml, clinical, life
- casehub-neocortex (memory backend implementations)

---

## Current State

All modules listed above are shipped except `preferences-editor/` item #8 (admin write path — 🔜).

Memory backend modules (`memory-inmem/`, `memory-jpa/`, `memory-sqlite/`, `memory-mem0/`, `memory-graphiti/`) were migrated to casehub-neocortex (neocortex#56). Directories remain on disk but are no longer in `<modules>`.

### Anti-Patterns

- Do not define parallel path, scope, preference, or principal types. `platform-api` owns these.
- Do not use `@ConfigMapping` for case-type business rules. Use `PreferenceProvider`.
- Do not call `SecurityIdentity` from `platform-api/`. Zero deps means zero Quarkus imports.
- Do not make `CurrentPrincipal` `@ApplicationScoped` in a real deployment. Real implementations must be `@RequestScoped`.
- Do not inject `Principal` directly in Quarkus. Use `SecurityIdentity` or `CurrentPrincipal`.

---

## Design Documents

- `ARC42STORIES.MD` — primary architecture record (layer taxonomy, building block view, glossary)
- ADRs in `docs/adr/`: 0001 (Path API), 0002 (PreferenceKey contract), 0003 (null-returning get)
- Garden protocols:
  - `casehub/garden: docs/protocols/casehub/typed-preference-keys.md` — `PreferenceKey<T>` contract
  - `casehub/garden: docs/protocols/casehub/platform-spi-contract.md` — implementation rules for SPIs
  - `casehub/garden: docs/protocols/universal/module-tier-structure.md` — Tier 1/2/3 rules
  - `casehub/garden: docs/protocols/universal/persistence-backend-cdi-priority.md` — CDI priority ladder
