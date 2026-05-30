# memory-sqlite Design Spec

**Issue:** casehubio/platform#37
**Date:** 2026-05-31
**Branch:** issue-37-memory-sqlite

---

## Overview

A new `memory-sqlite/` submodule providing a durable `CaseMemoryStore` adapter backed by
SQLite via `org.xerial:sqlite-jdbc`. No external database server — file-backed, single-process.
Designed for deployments that want persistent agent memory without running PostgreSQL.

---

## Prerequisite: Extract Abstract Contract Test Base

Before adding `SqliteMemoryStoreTest`, extract the shared contract test suite from
`JpaMemoryStoreTest` and `InMemoryMemoryStoreTest` into an abstract base class in `testing/`:

```java
// testing/src/test/java/io/casehub/platform/testing/memory/CaseMemoryStoreContractTest.java
public abstract class CaseMemoryStoreContractTest {
    protected abstract CaseMemoryStore store();
    // all ~25 contract @Test methods
}
```

Each adapter's test class extends this base. Adapter-specific tests (FTS paths, pool behaviour)
are additional methods in the concrete subclass. This extraction is a prerequisite step — without
it, adding the SQLite tests creates a third copy of the same assertions and makes future SPI
evolution expensive.

---

## Module Structure

```
memory-sqlite/
  pom.xml
  src/main/java/io/casehub/platform/memory/sqlite/
    SqliteMemoryStore.java   # CaseMemoryStore @Alternative @Priority(1) @ApplicationScoped
                             # @ConfigProperty fields inline — no @ConfigMapping interface
  src/main/resources/
    db/memory-sqlite/migration/
      V1__memory_sqlite_entry.sql  # table + indexes + FTS5 virtual table + triggers
  src/test/java/io/casehub/platform/memory/sqlite/
    SqliteMemoryStoreTest.java     # extends CaseMemoryStoreContractTest + SQLite-specific cases
  src/test/resources/
    application.properties         # casehub.memory.sqlite.path=:memory:
```

**Artifact:** `casehub-platform-memory-sqlite`

**CDI position:** `@Alternative @Priority(1) @ApplicationScoped`
Same tier as `memory-inmem`. Beats the JPA primary (`@ApplicationScoped`) and the no-op default
(`@DefaultBean`) when added to the classpath. Memory-sqlite and memory-inmem cannot coexist —
both are `@Priority(1)` for `CaseMemoryStore`; consumers pick one.

**No `quarkus:build` goal** — `CurrentPrincipal` is test-scope only; production augmentation
would fail if the build goal ran CDI validation. No `@ConfigMapping` interface — `@ConfigProperty`
annotations are used directly on the bean, which requires no build-time code generation and no
`generate-code` plugin goals. Jandex plugin added for CDI discovery.

---

## Dependencies

| Dependency | Scope | Notes |
|---|---|---|
| `casehub-platform-api` | compile | SPI + value types |
| `org.xerial:sqlite-jdbc` | compile | Standard JVM SQLite JDBC driver |
| `com.zaxxer:HikariCP` | compile | Connection pooling |
| `org.flywaydb:flyway-core` | compile | Programmatic schema migration at bean startup |
| `quarkus-jackson` | compile | Attribute JSON serialization — `ObjectMapper` CDI-injected |
| `casehub-platform` (mock module) | test | `MockCurrentPrincipal` |
| `quarkus-junit5` | test | `@QuarkusTest` |
| `jandex-maven-plugin` | build | CDI index |

`sqlite-jdbc` and `HikariCP` are not in the Quarkus BOM — versions pinned in the platform parent
POM. `flyway-core` is managed by the Quarkus BOM via `quarkus-flyway`; no explicit version needed.

`ObjectMapper` is CDI-injected from the `quarkus-jackson` extension (always present in any Quarkus
app that uses this adapter). It is not constructed directly — CDI injection ensures the application's
configured mapper instance is used.

---

## Configuration

Config prefix: `casehub.memory.sqlite` (matches `casehub.memory.jpa` in the JPA module).

```properties
# Required
casehub.memory.sqlite.path=./memory.db   # absolute path or :memory:

# Optional — with defaults shown
casehub.memory.sqlite.pool.max-size=5
casehub.memory.sqlite.busy-timeout-ms=5000
casehub.memory.sqlite.fts.enabled=true
```

No language property. SQLite FTS5 uses the `unicode61` tokenizer (whitespace/punctuation split,
unicode-aware) and does not support language-specific stemming. Exposing a language config that
does nothing would mislead callers.

**`:memory:` special case:** when `path=:memory:`, pool is forced to size 1 (otherwise each
HikariCP connection gets an isolated in-memory database) and WAL mode is skipped (SQLite does not
support WAL for in-memory databases). This makes `:memory:` safe for `@QuarkusTest` without
additional test infrastructure.

Note: `jdbc:sqlite:file::memory:?mode=memory&cache=shared&uri=true` would allow a pool larger
than 1 for in-memory mode, but requires URI-mode config that adds consumer complexity. Pool-size=1
for `:memory:` is the simpler contract.

---

## Schema

Migration at `classpath:db/memory-sqlite/migration/V1__memory_sqlite_entry.sql`.

```sql
CREATE TABLE IF NOT EXISTS memory_entry (
    memory_id  TEXT NOT NULL,
    tenant_id  TEXT NOT NULL,
    entity_id  TEXT NOT NULL,
    domain     TEXT NOT NULL,
    case_id    TEXT,
    text       TEXT NOT NULL,
    attributes TEXT NOT NULL DEFAULT '{}',
    -- Stored as truncated-to-millis ISO-8601 (always 24 chars: ...T10:15:30.000Z).
    -- Truncation is mandatory: Instant.toString() emits 0/3/6/9 fractional digits
    -- depending on precision; mixed widths sort incorrectly ('.'' < 'Z' in ASCII).
    created_at TEXT NOT NULL,
    PRIMARY KEY (memory_id)
);

CREATE INDEX IF NOT EXISTS memory_entry_lookup_idx
    ON memory_entry (tenant_id, entity_id, domain, created_at DESC);

CREATE INDEX IF NOT EXISTS memory_entry_erase_idx
    ON memory_entry (tenant_id, entity_id);

-- FTS5 content table: text column mirrored from memory_entry, maintained by triggers.
CREATE VIRTUAL TABLE IF NOT EXISTS memory_fts
    USING fts5(text, content='memory_entry', content_rowid='rowid');

CREATE TRIGGER IF NOT EXISTS memory_fts_ai AFTER INSERT ON memory_entry BEGIN
    INSERT INTO memory_fts(rowid, text) VALUES (new.rowid, new.text);
END;
CREATE TRIGGER IF NOT EXISTS memory_fts_ad AFTER DELETE ON memory_entry BEGIN
    INSERT INTO memory_fts(memory_fts, rowid, text) VALUES('delete', old.rowid, old.text);
END;
-- Defensive: CaseMemoryStore is append-only at the SPI level (no update method exists).
-- This trigger is included for correctness if a future implementation updates rows.
CREATE TRIGGER IF NOT EXISTS memory_fts_au AFTER UPDATE ON memory_entry BEGIN
    INSERT INTO memory_fts(memory_fts, rowid, text) VALUES('delete', old.rowid, old.text);
    INSERT INTO memory_fts(rowid, text) VALUES (new.rowid, new.text);
END;
```

**Timestamp storage:** `Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()` — always
produces exactly 24 characters (`2026-05-31T10:15:30.000Z`). Plain `Instant.toString()` is
insufficient: whole-second values produce 20 chars (`...Z`) which sorts before 24-char
millisecond values (`....000Z`) due to `.` (ASCII 46) < `Z` (ASCII 90), silently breaking
chronological ordering across rows with mixed fractional precision.

**Migration is self-managed.** Flyway runs programmatically at bean startup against the module's
own HikariCP DataSource. Consumers do not configure Flyway locations — this migration is
invisible to the Quarkus Flyway extension.

---

## Connection Lifecycle

`SqliteMemoryStore` manages a `HikariDataSource` directly.

**`@PostConstruct`:**
1. Detect `:memory:` path. Configure `SQLiteConfig` (xerial's type-safe PRAGMA API) and construct
   a `HikariDataSource` via `setDataSourceClassName("org.sqlite.SQLiteDataSource")`:

   ```java
   SQLiteConfig sqLiteConfig = new SQLiteConfig();
   sqLiteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);   // skipped for :memory:
   sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
   sqLiteConfig.setBusyTimeout(busyTimeoutMs);
   sqLiteConfig.setCacheSize(64000);  // pages (positive = pages, not kibibytes)

   HikariConfig hikariConfig = new HikariConfig();
   hikariConfig.setDataSourceClassName("org.sqlite.SQLiteDataSource");
   hikariConfig.addDataSourceProperty("url", "jdbc:sqlite:" + resolvedPath);
   hikariConfig.addDataSourceProperty("config", sqLiteConfig.toProperties());
   hikariConfig.setMaximumPoolSize(isMemory ? 1 : maxPoolSize);
   dataSource = new HikariDataSource(hikariConfig);
   ```

   `SQLiteConfig` is used (not URL params, not `connectionInitSql`) because URL PRAGMA
   application is version-sensitive in xerial and `connectionInitSql` is a single SQL statement
   — multiple PRAGMAs via semicolons are driver-dependent.

2. Run Flyway programmatically:
   ```java
   Flyway.configure()
       .dataSource(dataSource)
       .locations("classpath:db/memory-sqlite/migration")
       .load()
       .migrate();
   ```

**`@PreDestroy`:** close `HikariDataSource`.

**PRAGMA rationale:**
- `journal_mode=WAL` — readers never block writers; writers never block readers
- `synchronous=NORMAL` — safe on SSD; avoids full fsync per transaction
- `busy_timeout=5000` — wait up to 5 s on write contention before throwing
- `cache_size=64000` — 64,000 pages of page cache in memory

---

## SPI Implementation

`SqliteMemoryStore` uses plain JDBC (`Connection`, `PreparedStatement`, `ResultSet`).

**`assertTenant` first** (per `casememorystore-adapter-asserttenant-contract` protocol):
every method calls `MemoryPermissions.assertTenant(tenantId, principal)` before any JDBC call.

`CurrentPrincipal` is captured at method entry (before any JDBC work) — not inside a lambda or
nested call — to avoid CDI request-scope issues on pool executor threads.

**Dynamic `IN` clause:** `entityIds.size()` `?` placeholders generated at query time, bound
individually. Avoids SQL injection.

**`store()`** — INSERT with `UUID.randomUUID()`.
`created_at` = `Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()`. Auto-commit.

**`storeAll()`** — overrides the default (which calls `store()` N times in N separate
transactions). Wraps all INSERTs in a single explicit JDBC transaction:
```java
conn.setAutoCommit(false);
// N prepared INSERT statements
conn.commit();
```
FTS5 triggers fire within the same transaction, so FTS consistency is maintained. Each auto-commit
SQLite write incurs a WAL journal write; batching 20 memories into one transaction = 1 WAL write
instead of 20.

**`query()`** — two paths:
- `CHRONOLOGICAL` (default, or when `question` is null): `SELECT * FROM memory_entry WHERE ...
  ORDER BY created_at DESC LIMIT ?`
- `RELEVANCE` with non-null `question` and `fts.enabled=true`: FTS5 join query (see below)

**`erase()`** — `DELETE FROM memory_entry WHERE tenant_id = ? AND entity_id = ? AND domain = ?
[AND case_id = ?]`. Auto-commit.

**`eraseById()`** — `DELETE FROM memory_entry WHERE memory_id = ? AND tenant_id = ?`.
`tenant_id` in the WHERE clause is defence in depth: `assertTenant` guards at method entry, but
the WHERE clause remains even if the guard were relaxed for an admin path.

**`eraseEntity()`** — `DELETE FROM memory_entry WHERE tenant_id = ? AND entity_id = ?`.
Auto-commit.

FTS5 triggers handle index cleanup automatically on DELETE for all three erase methods.

---

## FTS Query (RELEVANCE order)

Activates when: `fts.enabled=true` AND `query.order() == RELEVANCE` AND `query.question() != null`.
Falls back to chronological when FTS is disabled OR `question` is null (even with RELEVANCE order).

**Question sanitisation:** strip FTS5 operator characters (`"`, `*`, `^`, `:`, `-`, `(`, `)`)
from the question string before binding. Do NOT wrap in double quotes — phrase-wrapping would
require the entire question to match as an exact word sequence, which is wrong for memory
retrieval (e.g. "side effects of ibuprofen" as a phrase query would miss documents containing
"ibuprofen" and "side effects" in different sentences). FTS5 default AND-of-terms semantics
(each space-separated word required) is the correct behaviour for this use case.

Stripping `-` is required: "pre-trial" without stripping becomes `pre MINUS trial` in FTS5
syntax, returning documents about `pre` that don't mention `trial`.

```sql
SELECT m.*
FROM memory_entry m
JOIN memory_fts ON memory_fts.rowid = m.rowid
WHERE m.tenant_id = ?
  AND m.entity_id IN (?, ...)
  AND m.domain = ?
  AND memory_fts MATCH ?
  [AND m.case_id = ?]
  [AND m.created_at >= ?]
ORDER BY rank
LIMIT ?
```

`rank` is the FTS5 built-in BM25 relevance column (negative; ascending = most relevant first).
Semantically equivalent to PostgreSQL's `ts_rank DESC` — scores are not comparable across backends.

---

## Testing

`SqliteMemoryStoreTest` extends `CaseMemoryStoreContractTest` (extracted as prerequisite above).

Test config (`src/test/resources/application.properties`):
```properties
casehub.memory.sqlite.path=:memory:
casehub.memory.sqlite.pool.max-size=1
```

`@AfterEach` calls `eraseEntity()` to reset state between tests (no JTA, no `@TestTransaction`).

**SQLite-specific test methods** (in addition to inherited contract tests):

| Method | What it covers |
|---|---|
| `queryWithRelevanceOrderUsesFts5()` | Two memories stored; one matches the question. Asserts matching memory returned first. |
| `queryWithRelevanceOrderFtsDisabled()` | `fts.enabled=false`; asserts chronological fallback (most-recent-first). |
| `queryWithRelevanceOrderNullQuestion()` | `order=RELEVANCE`, `question=null`; asserts chronological fallback. |
| `storeAllWrapsInSingleTransaction()` | Stores N memories via `storeAll()`; asserts all present and no partial state on simulated mid-batch failure. |

No Testcontainers needed — SQLite is fully embedded.

---

## Consumer Guide

Add to `pom.xml` (compile scope):
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-memory-sqlite</artifactId>
    <version>${project.version}</version>
</dependency>
```

Configure in `application.properties`:
```properties
casehub.memory.sqlite.path=/var/data/casehub/memory.db
```

No Flyway location configuration required. No additional Quarkus datasource block required.
`casehub-platform-memory-sqlite` displaces `NoOpCaseMemoryStore` and `JpaMemoryStore`
automatically via CDI priority.

Do NOT combine with `casehub-platform-memory-inmem` in the same scope — both are
`@Alternative @Priority(1)` for `CaseMemoryStore` and will produce an ambiguous resolution error.

---

## Known Limitations vs JPA Adapter

| Concern | JPA (PostgreSQL) | SQLite |
|---|---|---|
| FTS stemming | Language-specific (`english`, `french`) | None (unicode61 tokenizer only) |
| FTS ranking | `ts_rank` (positive, DESC) | BM25 / `rank` (negative, ASC) |
| Concurrent writers | Unlimited (PostgreSQL MVCC) | Single writer (WAL serialises) |
| Multi-process access | Supported | Not supported |
| Network deployment | Supported | Not supported (file lock) |
| `:memory:` pool size | N/A | Forced to 1; shared-cache URI mode not covered |

SQLite is the right choice for single-process, file-backed deployments. PostgreSQL (via
`memory-jpa`) is the right choice for multi-process or network deployments.
