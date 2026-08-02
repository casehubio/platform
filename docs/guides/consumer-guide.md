# casehub-platform — Consumer Guide

> Zero-dependency SPIs and types shared across all casehub modules — the foundation layer every app builds on.

**Repo:** [`casehubio/platform`](https://github.com/casehubio/platform)
**Tier:** Foundation (first in build order, zero casehubio dependencies)

---

## Purpose

casehub-platform defines the domain abstractions that every casehub module shares: identity, preferences, paths, memory, data sources, endpoints, notifications, subscriptions, expressions, access control, credentials, and governance. These are pure Java SPIs with zero external dependencies in `platform-api/`. Quarkus-specific implementations live in companion modules that activate by classpath presence via CDI `@DefaultBean` displacement.

This repo is not a parallel framework to Quarkus — it is a thin domain layer that Quarkus-specific code implements. `CurrentPrincipal` wraps `SecurityIdentity`, `PreferenceProvider` complements `@ConfigMapping`, and `Path` replaces `java.nio.file.Path` with domain semantics. They solve different problems and belong together.

---

## Modules to Depend On

### Always needed

| Artifact | What it gives you |
|----------|-------------------|
| `casehub-platform-api` | All SPIs and value types — zero deps, pure Java |
| `casehub-platform` | `@DefaultBean` mocks and no-ops — safe dev/test defaults |
| `casehub-platform-testing` (test scope) | `FixedCurrentPrincipal`, `InMemoryGroupMembershipProvider` — programmatic test control |

### Activate by adding as compile dependency

Each displaces its `@DefaultBean` mock automatically — no exclusion config needed.

| Artifact | What it activates |
|----------|-------------------|
| `casehub-platform-config` | Scope-aware YAML preference provider (replaces mock) |
| `casehub-platform-oidc` | OIDC-backed `CurrentPrincipal` from JWT (replaces mock) |
| `casehub-platform-scim` | SCIM 2.0 `GroupMembershipProvider` (replaces mock) |
| `casehub-platform-persistence-jpa` | JPA-backed scoped preference overrides |
| `casehub-platform-persistence-mongodb` | MongoDB preference backend (beats JPA when co-deployed) |
| `casehub-platform-credentials-quarkus` | Bridge `CredentialResolver` to Quarkus `CredentialsProvider` (Vault/AWS/GCP) |

### Notification system (add what you need)

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-notifications` | REST + SSE presentation layer |
| `casehub-platform-notifications-inmem` | In-memory notification store (test/ephemeral) |
| `casehub-platform-notifications-jpa` | JPA notification store (production) |
| `casehub-platform-notification-dispatch` | Three-path delivery pipeline (digest/suppress/immediate) |
| `casehub-platform-subscriptions` | Subscription matching engine + REST |
| `casehub-platform-subscriptions-inmem` | In-memory subscription store (test/ephemeral) |
| `casehub-platform-subscriptions-jpa` | JPA subscription store (production) |

### Data source and event streams

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-datasource-alpha` | Rete-style alpha network for event routing |
| `casehub-platform-datasource-inmem` | In-memory DataSource registry (test/ephemeral) |
| `casehub-platform-datasource-jpa` | JPA DataSource registry (production) |
| `casehub-platform-endpoints-memory` | In-memory endpoint registry |
| `casehub-platform-endpoints-config` | YAML-backed endpoint populator |
| `casehub-platform-streams-kafka` | Kafka event stream connector |
| `casehub-platform-streams-amqp` | AMQP event stream connector |
| `casehub-platform-streams-webhook` | Webhook event stream connector |
| `casehub-platform-streams-poll` | Polling event stream connector |
| `casehub-platform-streams-camel` | Apache Camel event stream connector (runtime-dynamic routes) |

### Agent infrastructure

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-agent-api` | `AgentProvider` SPI — single-shot and multi-turn (Mutiny only, no Quarkus) |
| `casehub-platform-agent-claude` | Claude CLI subprocess integration (autonomous agent) |
| `casehub-platform-agent-langchain4j` | Bidirectional LangChain4j interop (any ChatModel, any provider) |

### Access control

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-acl-inmem` | In-memory ACL store (test/ephemeral) |
| `casehub-platform-acl-jpa` | JPA ACL store with audit logging (production) |
| `casehub-platform-acl-admin` | REST API for ACL administration |

---

## Key Abstractions and SPIs

### Identity

| SPI | Purpose | Mock behaviour |
|-----|---------|----------------|
| `CurrentPrincipal` | Who is acting — `actorId()`, `groups()`, `tenancyId()` | `@ApplicationScoped` with `@ConfigProperty` values |
| `GroupMembershipProvider` | Inverse membership — "who is in group X?" | Returns configured groups |

`CurrentPrincipal` is not `SecurityIdentity`. casehub actors include AI agents, system actors, and internal services that operate outside HTTP request context. Real implementations are `@RequestScoped` and delegate to `SecurityIdentity`; the mock is `@ApplicationScoped` (no request context in dev/test).

**Tenancy:** `tenancyId()` is abstract — every implementor must provide it. Single-tenant deployments return `TenancyConstants.DEFAULT_TENANT_ID`. `isCrossTenantAdmin()` controls cross-tenant data access.

### Path

Hierarchical, scope-labelling type for case types, preference scopes, label paths. Not a filesystem path — strict validation, no empty segments, no leading/trailing slashes.

```java
Path.of("casehubio", "devtown", "pr-review")  // explicit construction
Path.parse("casehubio/devtown/pr-review")      // uses configured separator
```

Convention: org segment / app segment / case-type segment. Scope inheritance follows the hierarchy.

JAX-RS integration: `@PathParam` and `@QueryParam` of type `Path` work directly — converters ship in `platform/`.

### Preferences

| | SmallRye Config | `PreferenceProvider` |
|--|--|--|
| When resolved | Startup | Per-request, per scope |
| Can change without restart | No | Yes |
| Varies per case type | No | Yes |
| Scope hierarchy | No | `casehubio` -> `devtown` -> `pr-review` |

SmallRye Config is for deployment configuration (DB URLs, pool sizes). `PreferenceProvider` is for business configuration (rules that vary per case type and installation). They complement each other.

`PreferenceKey<T>` carries a parser — `key.parse(raw)` converts strings from any source. Use `key.qualifiedName()` as map keys, never the `PreferenceKey` object (records with `Function` components have identity-only equality).

### DataSource and Alpha Network

Rete-style event routing: `DataSource<T>` ingests objects, `ObjectType<T>` discriminates by type, `FilterExpression<T>` evaluates predicates. Four `subscribe()` overloads with increasing specificity. Self-pruning deregistration lifecycle handles shutdown gracefully.

`DataSourceRegistry` is tenant-scoped — `resolve(Path, tenancyId)` returns tenant-specific before platform-global.

### Notifications and Subscriptions

Domain modules produce `SubscribableEvent` objects into the notification DataSource. The subscription engine evaluates them against the alpha network, fires `SubscriptionMatched`, and the dispatch pipeline handles delivery (immediate, digest, or suppressed). REST + SSE endpoints expose notifications to clients.

### Expression Evaluation

`ExpressionEngineRegistry` dispatches by type key — `"jq"` (jackson-jq, `JsonNode` context) and `"mvel"` (MVEL3, `Map<String, Object>` context). Used by the subscription engine for filter compilation.

### Access Control

`AccessControlProvider` provides async access control with resource hierarchy inheritance. Group-based grants resolve via `GroupMembershipProvider`. Parent-child hierarchy with depth guard of 20. Deny entries with specificity-based resolution (instance deny/grant -> wildcard deny/grant -> parent chain).

### CaseMemoryStore

Cross-case semantic recall. `CaseMemoryStore` (blocking) provides `store`, `query`, `erase`. Domain isolation via `MemoryDomain` — facts do not cross domain boundaries. `MemoryPermissions` enforces tenant access at the SPI layer. `@DefaultBean` is a silent no-op — the system functions correctly without memory.

Backend implementations live in casehub-neocortex, not this repo.

### Credentials

`CredentialResolver` resolves outbound endpoint credentials by logical reference name. Returns `Map<String, String>` keyed by `CredentialPropertyKeys` constants. Distinct from inbound Verifiable Credential validation in identity.

### Governance

`ExecutionPolicy` + `RetryPolicy` + `BackoffStrategy` — generic retry/timeout/backoff for blocking operations via `PolicyEnforcer`.

---

## Configuration

### Identity

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.tenancy.default-id` | Default tenant ID for single-tenant deployments | (TenancyConstants value) |
| `casehub.platform.scim.token` | Static SCIM auth token | — |
| `quarkus.oidc-client.scim.*` | SCIM client-credentials auth | — |
| `casehub.identity.dids."actorId"` | Static actor-to-DID mapping | — |
| `casehub.identity.credentials."actorId"` | VC JWT file paths | — |

### Preferences

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.platform.path.separator` | Path separator character | `/` |
| `casehub.platform.endpoints.files` | YAML endpoint definition files | — |

### Agent

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.platform.agent.langchain4j.closeTimeout` | Session close timeout | PT30S |
| `casehub.platform.agent.langchain4j.max-concurrent-sessions` | Concurrent session limit | 10 |

### Streams

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.streams.webhook.public-url` | Public URL for webhook self-registration | (required) |
| `casehub.streams.poll.interval` | Polling interval | 60s |

### Notifications

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.notification.digest.max-buffer-size` | Digest buffer size (0 = no eviction) | 0 |
| `casehub.delivery.tracking.inmem.max-size` | In-memory delivery attempt store size | 10000 |
| `casehub.delivery.engagement.enabled` | Enable engagement event recording | false |
| `casehub.delivery.retention.attempt-days` | Delivery attempt retention | — |
| `casehub.delivery.retention.failed-attempt-days` | Failed attempt retention | — |
| `casehub.delivery.retention.engagement-days` | Engagement event retention | — |

### ACL

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.acl.retention.expired-purge-cron` | Expired entry purge schedule | daily 03:00 |
| `casehub.acl.retention.audit-purge-cron` | Audit log purge schedule | daily 03:30 |
| `casehub.acl.retention.audit-days` | Audit log retention | 365 |

### Flyway locations (add to consumer's config)

| Module | Flyway location |
|--------|----------------|
| `persistence-jpa` | `classpath:db/platform/migration` |
| `datasource-jpa` | `classpath:db/datasource/migration` |
| `notifications-jpa` | `classpath:db/notification/migration` |
| `notification-settings-jpa` | `classpath:db/notification-settings/migration` |
| `delivery-tracking-jpa` | `classpath:db/delivery-tracking/migration` |
| `digest-jpa` | `classpath:db/digest/migration` |
| `subscriptions-jpa` | `classpath:db/subscription/migration` |
| `acl-jpa` | `classpath:db/acl/migration` |

---

## What This Repo Does NOT Do

- **Domain logic.** No case definitions, work items, or business rules. Those live in consumer repos (ledger, work, engine, devtown, etc.).
- **Memory backends.** `CaseMemoryStore` SPI is here; implementations (in-mem, JPA, SQLite, Mem0, Graphiti) live in casehub-neocortex.
- **Preference writes.** `PreferenceProvider` is permanently read-only. The `preferences-editor/` module provides the write path, but the provider never owns writes.
- **Security enforcement beyond tenancy.** `CurrentPrincipal` provides identity. `@RolesAllowed` and full RBAC are Quarkus concerns, not platform concerns.
- **Orchestration.** Event routing and subscription matching happen here. Case orchestration, planning, and execution live in casehub-engine.
