# Stage 1: Registry Core — Platform Registration Service

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #267 — modular fleet architecture
**Issue group:** #267 (epic), platform-side only

**Goal:** Create a unified registration SPI in `casehub-platform-api` with heartbeat, TTL, typed entities, relationships, and health status — extending the registry pattern alongside `EndpointRegistry`.

**Architecture:** New `RegistryService` SPI in `platform-api` as a sibling to `EndpointRegistry` (not an extension — the key model differs: `EndpointRegistry` uses `(Path, tenancyId)` for named endpoints; `RegistryService` uses `(id, type, namespace)` for runtime service topology). InMemory implementation in a new `registry-memory` module. JPA implementation deferred to Stage 3 (ops-tier). Heartbeat scheduler as a separate `@ApplicationScoped` bean.

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI, JUnit 5

**Repo:** `casehub-platform` (`~/claude/casehub/platform`)

## Global Constraints

- Java 21 API surface (compile on Java 26)
- Quarkus 3.32.2
- Follow `EndpointRegistry` patterns: `@DefaultBean` NoOp in `platform-api`, `@Alternative @Priority` for real implementations
- SPI in `platform-api` must have zero dependencies beyond Java SE + CDI annotations
- All new types are records (immutable)
- Fire CDI events on state transitions (`Event.fireAsync()`)

---

## Batch 1: SPI Types

### Task 1: Registry entry and relationship types

**Files:**
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/RegistryEntry.java`
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/Relationship.java`
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/HealthStatus.java`
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/RegistryQuery.java`
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/RegistryEvent.java`
- Test: `platform-api/src/test/java/io/casehub/platform/api/registry/RegistryEntryTest.java`

**Interfaces:**
- Consumes: nothing (foundation types)
- Produces: `RegistryEntry`, `Relationship`, `HealthStatus`, `RegistryQuery`, `RegistryEvent` — used by all subsequent tasks

- [ ] **Step 1: Write tests for RegistryEntry record**

```java
package io.casehub.platform.api.registry;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RegistryEntryTest {

    @Test
    void minimalEntry() {
        var entry = new RegistryEntry(
            "fleet-1", "service", "default", "tenant-1",
            Map.of(), Instant.now(), null, Duration.ofSeconds(30),
            HealthStatus.HEALTHY
        );
        assertThat(entry.id()).isEqualTo("fleet-1");
        assertThat(entry.type()).isEqualTo("service");
        assertThat(entry.namespace()).isEqualTo("default");
        assertThat(entry.health()).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void nullIdThrows() {
        assertThatThrownBy(() -> new RegistryEntry(
            null, "service", "default", "t", Map.of(),
            Instant.now(), null, Duration.ofSeconds(30), HealthStatus.HEALTHY
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void propertiesAreImmutable() {
        var props = new java.util.HashMap<String, String>();
        props.put("key", "value");
        var entry = new RegistryEntry(
            "e1", "service", "default", "t", props,
            Instant.now(), null, Duration.ofSeconds(30), HealthStatus.HEALTHY
        );
        assertThatThrownBy(() -> entry.metadata().put("new", "val"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void relationshipRecord() {
        var rel = new Relationship("app-1", "pool-1", "owns");
        assertThat(rel.sourceId()).isEqualTo("app-1");
        assertThat(rel.targetId()).isEqualTo("pool-1");
        assertThat(rel.type()).isEqualTo("owns");
    }

    @Test
    void registryQueryFiltering() {
        var query = new RegistryQuery("tenant-1", "service", "default");
        assertThat(query.tenancyId()).isEqualTo("tenant-1");
        assertThat(query.type()).isEqualTo("service");
        assertThat(query.namespace()).isEqualTo("default");
    }

    @Test
    void registryQueryWildcards() {
        var query = new RegistryQuery("tenant-1", null, null);
        assertThat(query.type()).isNull();
        assertThat(query.namespace()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl platform-api -Dtest=RegistryEntryTest -f ~/claude/casehub/platform/pom.xml`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement the types**

`HealthStatus.java`:
```java
package io.casehub.platform.api.registry;

public enum HealthStatus {
    HEALTHY, DEGRADED, DOWN
}
```

`RegistryEntry.java`:
```java
package io.casehub.platform.api.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RegistryEntry(
        String id,
        String type,
        String namespace,
        String tenancyId,
        Map<String, String> metadata,
        Instant registeredAt,
        Instant lastHeartbeat,
        Duration ttl,
        HealthStatus health
) {
    public RegistryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(health, "health");
        metadata = Map.copyOf(metadata);
    }

    public RegistryEntry withHealth(HealthStatus newHealth) {
        return new RegistryEntry(id, type, namespace, tenancyId,
            metadata, registeredAt, lastHeartbeat, ttl, newHealth);
    }

    public RegistryEntry withHeartbeat(Instant now) {
        return new RegistryEntry(id, type, namespace, tenancyId,
            metadata, registeredAt, now, ttl, HealthStatus.HEALTHY);
    }
}
```

`Relationship.java`:
```java
package io.casehub.platform.api.registry;

import java.util.Objects;

public record Relationship(String sourceId, String targetId, String type) {
    public Relationship {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(type, "type");
    }
}
```

`RegistryQuery.java`:
```java
package io.casehub.platform.api.registry;

import java.util.Objects;

public record RegistryQuery(
        String tenancyId,
        String type,
        String namespace
) {
    public RegistryQuery {
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
```

`RegistryEvent.java`:
```java
package io.casehub.platform.api.registry;

import java.util.Objects;

public record RegistryEvent(
        EventKind kind,
        RegistryEntry entry,
        Relationship relationship
) {
    public enum EventKind {
        REGISTERED, DEREGISTERED, HEALTH_CHANGED,
        HEARTBEAT_EXPIRED, LINKED, UNLINKED
    }

    public RegistryEvent {
        Objects.requireNonNull(kind, "kind");
    }

    public static RegistryEvent registered(RegistryEntry entry) {
        return new RegistryEvent(EventKind.REGISTERED, entry, null);
    }

    public static RegistryEvent deregistered(RegistryEntry entry) {
        return new RegistryEvent(EventKind.DEREGISTERED, entry, null);
    }

    public static RegistryEvent healthChanged(RegistryEntry entry) {
        return new RegistryEvent(EventKind.HEALTH_CHANGED, entry, null);
    }

    public static RegistryEvent heartbeatExpired(RegistryEntry entry) {
        return new RegistryEvent(EventKind.HEARTBEAT_EXPIRED, entry, null);
    }

    public static RegistryEvent linked(Relationship rel) {
        return new RegistryEvent(EventKind.LINKED, null, rel);
    }

    public static RegistryEvent unlinked(Relationship rel) {
        return new RegistryEvent(EventKind.UNLINKED, null, rel);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl platform-api -Dtest=RegistryEntryTest -f ~/claude/casehub/platform/pom.xml`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git -C ~/claude/casehub/platform add platform-api/src/main/java/io/casehub/platform/api/registry/ platform-api/src/test/java/io/casehub/platform/api/registry/
git -C ~/claude/casehub/platform commit -m "feat(registry): add RegistryEntry, Relationship, HealthStatus, RegistryQuery, RegistryEvent types Refs casehubio/claudony#267"
```

---

## Batch 2: SPI and NoOp Default

### Task 2: RegistryService SPI and NoOp implementation

**Files:**
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/RegistryService.java`
- Create: `platform-api/src/main/java/io/casehub/platform/api/registry/NoOpRegistryService.java`
- Test: `platform-api/src/test/java/io/casehub/platform/api/registry/NoOpRegistryServiceTest.java`

**Interfaces:**
- Consumes: `RegistryEntry`, `Relationship`, `RegistryQuery`, `RegistryEvent` from Task 1
- Produces: `RegistryService` interface — consumed by InMemory impl (Task 3), all migration tasks (Stage 2), and all consumers

- [ ] **Step 1: Write tests for NoOp behaviour**

```java
package io.casehub.platform.api.registry;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class NoOpRegistryServiceTest {

    private final RegistryService registry = new NoOpRegistryService();

    private RegistryEntry entry(String id, String type) {
        return new RegistryEntry(id, type, "default", "tenant-1",
            Map.of(), Instant.now(), null, Duration.ofSeconds(30),
            HealthStatus.HEALTHY);
    }

    @Test
    void registerAndResolveReturnsEmpty() {
        registry.register(entry("e1", "service"));
        assertThat(registry.resolve("e1")).isEmpty();
    }

    @Test
    void discoverReturnsEmpty() {
        registry.register(entry("e1", "service"));
        var query = new RegistryQuery("tenant-1", "service", "default");
        assertThat(registry.discover(query)).isEmpty();
    }

    @Test
    void heartbeatNoOp() {
        assertThatNoException().isThrownBy(() -> registry.heartbeat("e1"));
    }

    @Test
    void deregisterNoOp() {
        assertThatNoException().isThrownBy(() -> registry.deregister("e1"));
    }

    @Test
    void linkAndRelationshipsReturnEmpty() {
        registry.link(new Relationship("a", "b", "owns"));
        assertThat(registry.relationships("a")).isEmpty();
    }

    @Test
    void watchNoOp() {
        var events = new ArrayList<RegistryEvent>();
        registry.watch(new RegistryQuery("t", null, null), events::add);
        registry.register(entry("e1", "service"));
        assertThat(events).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl platform-api -Dtest=NoOpRegistryServiceTest -f ~/claude/casehub/platform/pom.xml`
Expected: FAIL — interface and class not found

- [ ] **Step 3: Implement RegistryService interface**

```java
package io.casehub.platform.api.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface RegistryService {

    void register(RegistryEntry entry);

    void heartbeat(String id);

    void deregister(String id);

    Optional<RegistryEntry> resolve(String id);

    List<RegistryEntry> discover(RegistryQuery query);

    void link(Relationship rel);

    void unlink(String sourceId, String targetId);

    List<Relationship> relationships(String id);

    void watch(RegistryQuery query, Consumer<RegistryEvent> listener);
}
```

- [ ] **Step 4: Implement NoOpRegistryService**

```java
package io.casehub.platform.api.registry;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@DefaultBean
@ApplicationScoped
public class NoOpRegistryService implements RegistryService {

    @Override public void register(RegistryEntry entry) {}
    @Override public void heartbeat(String id) {}
    @Override public void deregister(String id) {}
    @Override public Optional<RegistryEntry> resolve(String id) { return Optional.empty(); }
    @Override public List<RegistryEntry> discover(RegistryQuery query) { return List.of(); }
    @Override public void link(Relationship rel) {}
    @Override public void unlink(String sourceId, String targetId) {}
    @Override public List<Relationship> relationships(String id) { return List.of(); }
    @Override public void watch(RegistryQuery query, Consumer<RegistryEvent> listener) {}
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl platform-api -Dtest=NoOpRegistryServiceTest -f ~/claude/casehub/platform/pom.xml`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git -C ~/claude/casehub/platform add platform-api/src/main/java/io/casehub/platform/api/registry/ platform-api/src/test/java/io/casehub/platform/api/registry/
git -C ~/claude/casehub/platform commit -m "feat(registry): add RegistryService SPI and NoOp default bean Refs casehubio/claudony#267"
```

---

## Batch 3: InMemory Implementation

### Task 3: InMemoryRegistryService with heartbeat and watch

**Files:**
- Create: `registry-memory/pom.xml` (new Maven module)
- Create: `registry-memory/src/main/java/io/casehub/platform/registry/memory/InMemoryRegistryService.java`
- Create: `registry-memory/src/main/java/io/casehub/platform/registry/memory/quarkus/RegistryMemoryBeans.java`
- Modify: `pom.xml` (root — add `registry-memory` to `<modules>`)
- Test: `registry-memory/src/test/java/io/casehub/platform/registry/memory/InMemoryRegistryServiceTest.java`

**Interfaces:**
- Consumes: `RegistryService`, `RegistryEntry`, `Relationship`, `RegistryQuery`, `RegistryEvent` from Tasks 1-2
- Produces: `InMemoryRegistryService` — working in-memory registry for standalone Claudony and tests

- [ ] **Step 1: Create Maven module**

Create `registry-memory/pom.xml` following the `endpoints-memory` module pattern. Parent is platform parent POM. Dependency on `platform-api`. Test dependencies: JUnit 5, AssertJ.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-platform-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <artifactId>casehub-platform-registry-memory</artifactId>
    <name>CaseHub Platform — Registry (In-Memory)</name>
    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Add `<module>registry-memory</module>` to the root POM's `<modules>` section.

- [ ] **Step 2: Write tests for InMemoryRegistryService**

```java
package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class InMemoryRegistryServiceTest {

    private InMemoryRegistryService registry;
    private final ArrayList<RegistryEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryService(e -> {});
        events.clear();
    }

    private RegistryEntry entry(String id, String type, String namespace) {
        return new RegistryEntry(id, type, namespace, "tenant-1",
            Map.of(), Instant.now(), null, Duration.ofSeconds(30),
            HealthStatus.HEALTHY);
    }

    @Test
    void registerAndResolve() {
        var e = entry("svc-1", "service", "default");
        registry.register(e);
        assertThat(registry.resolve("svc-1")).isPresent()
            .hasValueSatisfying(r -> assertThat(r.id()).isEqualTo("svc-1"));
    }

    @Test
    void resolveUnknownReturnsEmpty() {
        assertThat(registry.resolve("nope")).isEmpty();
    }

    @Test
    void registerUpserts() {
        registry.register(entry("svc-1", "service", "default"));
        var updated = new RegistryEntry("svc-1", "service", "default", "tenant-1",
            Map.of("key", "val"), Instant.now(), null, Duration.ofSeconds(30),
            HealthStatus.HEALTHY);
        registry.register(updated);
        assertThat(registry.resolve("svc-1").get().metadata())
            .containsEntry("key", "val");
    }

    @Test
    void deregisterRemovesEntry() {
        registry.register(entry("svc-1", "service", "default"));
        registry.deregister("svc-1");
        assertThat(registry.resolve("svc-1")).isEmpty();
    }

    @Test
    void deregisterRemovesRelationships() {
        registry.register(entry("app-1", "app", "ns"));
        registry.register(entry("pool-1", "pool", "ns"));
        registry.link(new Relationship("app-1", "pool-1", "owns"));
        registry.deregister("app-1");
        assertThat(registry.relationships("app-1")).isEmpty();
        assertThat(registry.relationships("pool-1")).isEmpty();
    }

    @Test
    void discoverByType() {
        registry.register(entry("svc-1", "service", "default"));
        registry.register(entry("pool-1", "pool", "default"));
        var results = registry.discover(new RegistryQuery("tenant-1", "service", null));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("svc-1");
    }

    @Test
    void discoverByNamespace() {
        registry.register(entry("svc-1", "service", "app-a"));
        registry.register(entry("svc-2", "service", "app-b"));
        var results = registry.discover(new RegistryQuery("tenant-1", null, "app-a"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("svc-1");
    }

    @Test
    void discoverByTypeAndNamespace() {
        registry.register(entry("svc-1", "service", "app-a"));
        registry.register(entry("pool-1", "pool", "app-a"));
        var results = registry.discover(new RegistryQuery("tenant-1", "pool", "app-a"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("pool-1");
    }

    @Test
    void discoverFiltersByTenancy() {
        registry.register(entry("svc-1", "service", "default"));
        var results = registry.discover(new RegistryQuery("other-tenant", null, null));
        assertThat(results).isEmpty();
    }

    @Test
    void heartbeatUpdatesTimestamp() {
        registry.register(entry("svc-1", "service", "default"));
        var before = registry.resolve("svc-1").get().lastHeartbeat();
        registry.heartbeat("svc-1");
        var after = registry.resolve("svc-1").get().lastHeartbeat();
        assertThat(after).isNotNull();
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void heartbeatUnknownIdNoOp() {
        assertThatNoException().isThrownBy(() -> registry.heartbeat("nope"));
    }

    @Test
    void linkAndQueryRelationships() {
        registry.register(entry("app-1", "app", "ns"));
        registry.register(entry("pool-1", "pool", "ns"));
        registry.link(new Relationship("app-1", "pool-1", "owns"));
        assertThat(registry.relationships("app-1"))
            .hasSize(1)
            .first().satisfies(r -> {
                assertThat(r.sourceId()).isEqualTo("app-1");
                assertThat(r.targetId()).isEqualTo("pool-1");
            });
        assertThat(registry.relationships("pool-1"))
            .hasSize(1);
    }

    @Test
    void unlinkRemovesRelationship() {
        registry.register(entry("app-1", "app", "ns"));
        registry.register(entry("pool-1", "pool", "ns"));
        registry.link(new Relationship("app-1", "pool-1", "owns"));
        registry.unlink("app-1", "pool-1");
        assertThat(registry.relationships("app-1")).isEmpty();
    }

    @Test
    void watchReceivesRegistrationEvents() {
        registry.watch(new RegistryQuery("tenant-1", null, null), events::add);
        registry.register(entry("svc-1", "service", "default"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo(RegistryEvent.EventKind.REGISTERED);
    }

    @Test
    void watchFiltersbyType() {
        registry.watch(new RegistryQuery("tenant-1", "pool", null), events::add);
        registry.register(entry("svc-1", "service", "default"));
        registry.register(entry("pool-1", "pool", "default"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).entry().id()).isEqualTo("pool-1");
    }

    @Test
    void watchReceivesDeregistrationEvents() {
        registry.register(entry("svc-1", "service", "default"));
        registry.watch(new RegistryQuery("tenant-1", null, null), events::add);
        registry.deregister("svc-1");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo(RegistryEvent.EventKind.DEREGISTERED);
    }

    @Test
    void cdiEventFiredOnRegister() {
        var cdiEvents = new ArrayList<RegistryEvent>();
        var reg = new InMemoryRegistryService(cdiEvents::add);
        reg.register(entry("svc-1", "service", "default"));
        assertThat(cdiEvents).hasSize(1);
        assertThat(cdiEvents.get(0).kind()).isEqualTo(RegistryEvent.EventKind.REGISTERED);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl registry-memory -Dtest=InMemoryRegistryServiceTest -f ~/claude/casehub/platform/pom.xml`
Expected: FAIL — class not found

- [ ] **Step 4: Implement InMemoryRegistryService**

```java
package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InMemoryRegistryService implements RegistryService {

    private final ConcurrentHashMap<String, RegistryEntry> entries = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Relationship> relationships = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WatchSubscription> watchers = new CopyOnWriteArrayList<>();
    private final Consumer<RegistryEvent> cdiEventSink;

    public InMemoryRegistryService(Consumer<RegistryEvent> cdiEventSink) {
        this.cdiEventSink = Objects.requireNonNull(cdiEventSink);
    }

    @Override
    public void register(RegistryEntry entry) {
        entries.put(entry.id(), entry);
        var event = RegistryEvent.registered(entry);
        cdiEventSink.accept(event);
        notifyWatchers(event);
    }

    @Override
    public void heartbeat(String id) {
        entries.computeIfPresent(id, (k, e) -> e.withHeartbeat(Instant.now()));
    }

    @Override
    public void deregister(String id) {
        var removed = entries.remove(id);
        if (removed != null) {
            relationships.removeIf(r ->
                r.sourceId().equals(id) || r.targetId().equals(id));
            var event = RegistryEvent.deregistered(removed);
            cdiEventSink.accept(event);
            notifyWatchers(event);
        }
    }

    @Override
    public Optional<RegistryEntry> resolve(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<RegistryEntry> discover(RegistryQuery query) {
        return entries.values().stream()
            .filter(e -> e.tenancyId().equals(query.tenancyId()))
            .filter(e -> query.type() == null || e.type().equals(query.type()))
            .filter(e -> query.namespace() == null || e.namespace().equals(query.namespace()))
            .toList();
    }

    @Override
    public void link(Relationship rel) {
        relationships.add(rel);
        cdiEventSink.accept(RegistryEvent.linked(rel));
    }

    @Override
    public void unlink(String sourceId, String targetId) {
        relationships.removeIf(r ->
            r.sourceId().equals(sourceId) && r.targetId().equals(targetId));
    }

    @Override
    public List<Relationship> relationships(String id) {
        return relationships.stream()
            .filter(r -> r.sourceId().equals(id) || r.targetId().equals(id))
            .toList();
    }

    @Override
    public void watch(RegistryQuery query, Consumer<RegistryEvent> listener) {
        watchers.add(new WatchSubscription(query, listener));
    }

    private void notifyWatchers(RegistryEvent event) {
        for (var sub : watchers) {
            if (matchesWatch(sub.query(), event)) {
                sub.listener().accept(event);
            }
        }
    }

    private boolean matchesWatch(RegistryQuery query, RegistryEvent event) {
        var entry = event.entry();
        if (entry == null) return false;
        if (!entry.tenancyId().equals(query.tenancyId())) return false;
        if (query.type() != null && !entry.type().equals(query.type())) return false;
        if (query.namespace() != null && !entry.namespace().equals(query.namespace())) return false;
        return true;
    }

    private record WatchSubscription(RegistryQuery query, Consumer<RegistryEvent> listener) {}
}
```

- [ ] **Step 5: Implement CDI producer (RegistryMemoryBeans)**

```java
package io.casehub.platform.registry.memory.quarkus;

import io.casehub.platform.api.registry.RegistryEvent;
import io.casehub.platform.registry.memory.InMemoryRegistryService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegistryMemoryBeans {

    @Inject Event<RegistryEvent> registryEvent;

    @Produces
    @Alternative
    @Priority(50)
    @ApplicationScoped
    public InMemoryRegistryService inMemoryRegistryService() {
        return new InMemoryRegistryService(e -> registryEvent.fireAsync(e));
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl registry-memory -Dtest=InMemoryRegistryServiceTest -f ~/claude/casehub/platform/pom.xml`
Expected: PASS (17 tests)

- [ ] **Step 7: Commit**

```bash
git -C ~/claude/casehub/platform add registry-memory/ pom.xml
git -C ~/claude/casehub/platform commit -m "feat(registry): add InMemoryRegistryService with heartbeat, relationships, watch Refs casehubio/claudony#267"
```

---

## Batch 4: Heartbeat Scheduler

### Task 4: HeartbeatScheduler — TTL expiry for stale entries

**Files:**
- Create: `registry-memory/src/main/java/io/casehub/platform/registry/memory/HeartbeatScheduler.java`
- Test: `registry-memory/src/test/java/io/casehub/platform/registry/memory/HeartbeatSchedulerTest.java`

**Interfaces:**
- Consumes: `InMemoryRegistryService` from Task 3, `RegistryEntry`, `HealthStatus`, `RegistryEvent`
- Produces: `HeartbeatScheduler` — marks entries DOWN when TTL expires, fires `HEARTBEAT_EXPIRED` event

- [ ] **Step 1: Write tests**

```java
package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class HeartbeatSchedulerTest {

    private InMemoryRegistryService registry;
    private HeartbeatScheduler scheduler;
    private final ArrayList<RegistryEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryService(events::add);
        scheduler = new HeartbeatScheduler(registry);
    }

    private RegistryEntry entryWithTtl(String id, Duration ttl, Instant lastHeartbeat) {
        return new RegistryEntry(id, "service", "default", "tenant-1",
            Map.of(), Instant.now(), lastHeartbeat, ttl, HealthStatus.HEALTHY);
    }

    @Test
    void healthyEntryWithRecentHeartbeatUnchanged() {
        var entry = entryWithTtl("svc-1", Duration.ofSeconds(30), Instant.now());
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-1").get().health()).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void expiredEntryMarkedDown() {
        var entry = entryWithTtl("svc-1", Duration.ofSeconds(1),
            Instant.now().minus(Duration.ofSeconds(5)));
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-1").get().health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void expiredEntryFiresEvent() {
        var entry = entryWithTtl("svc-1", Duration.ofSeconds(1),
            Instant.now().minus(Duration.ofSeconds(5)));
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(events).anyMatch(e ->
            e.kind() == RegistryEvent.EventKind.HEALTH_CHANGED &&
            e.entry().id().equals("svc-1"));
    }

    @Test
    void nullHeartbeatUsesRegisteredAt() {
        var longAgo = Instant.now().minus(Duration.ofSeconds(60));
        var entry = new RegistryEntry("svc-1", "service", "default", "tenant-1",
            Map.of(), longAgo, null, Duration.ofSeconds(5), HealthStatus.HEALTHY);
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-1").get().health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void alreadyDownEntryNotReprocessed() {
        var entry = entryWithTtl("svc-1", Duration.ofSeconds(1),
            Instant.now().minus(Duration.ofSeconds(5)));
        registry.register(entry);
        scheduler.checkHeartbeats();
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(events.stream()
            .filter(e -> e.kind() == RegistryEvent.EventKind.HEALTH_CHANGED)
            .count()).isZero();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl registry-memory -Dtest=HeartbeatSchedulerTest -f ~/claude/casehub/platform/pom.xml`
Expected: FAIL — HeartbeatScheduler not found

- [ ] **Step 3: Implement HeartbeatScheduler**

```java
package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEvent;
import io.casehub.platform.api.registry.RegistryQuery;

import java.time.Instant;

public class HeartbeatScheduler {

    private final InMemoryRegistryService registry;

    public HeartbeatScheduler(InMemoryRegistryService registry) {
        this.registry = registry;
    }

    public void checkHeartbeats() {
        var now = Instant.now();
        var all = registry.discover(new RegistryQuery(
            io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID,
            null, null));
        // Discover only returns tenant-filtered results; scan entries directly
        registry.allEntries().forEach(entry -> {
            if (entry.health() == HealthStatus.DOWN) return;
            var lastSeen = entry.lastHeartbeat() != null
                ? entry.lastHeartbeat()
                : entry.registeredAt();
            if (now.isAfter(lastSeen.plus(entry.ttl()))) {
                var downEntry = entry.withHealth(HealthStatus.DOWN);
                registry.updateEntry(downEntry);
            }
        });
    }
}
```

This requires adding two package-private methods to `InMemoryRegistryService`:

```java
// Add to InMemoryRegistryService:
List<RegistryEntry> allEntries() {
    return List.copyOf(entries.values());
}

void updateEntry(RegistryEntry entry) {
    var previous = entries.put(entry.id(), entry);
    if (previous != null && previous.health() != entry.health()) {
        var event = RegistryEvent.healthChanged(entry);
        cdiEventSink.accept(event);
        notifyWatchers(event);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl registry-memory -Dtest=HeartbeatSchedulerTest -f ~/claude/casehub/platform/pom.xml`
Expected: PASS (5 tests)

- [ ] **Step 5: Run all registry tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl platform-api,registry-memory -f ~/claude/casehub/platform/pom.xml`
Expected: PASS (all 28 tests across both modules)

- [ ] **Step 6: Commit**

```bash
git -C ~/claude/casehub/platform add registry-memory/src/ platform-api/src/
git -C ~/claude/casehub/platform commit -m "feat(registry): add HeartbeatScheduler for TTL-based health expiry Refs casehubio/claudony#267"
```

---

## References

- `2026-10-09-modular-fleet-architecture-design.md` — design spec this plan implements
- `platform-api/.../endpoints/EndpointRegistry.java` — existing registration SPI pattern
- `platform-api/.../endpoints/EndpointDescriptor.java` — existing entry type pattern
- `endpoints-memory/.../EndpointsMemoryBeans.java` — CDI producer pattern for in-memory impl
- casehubio/claudony#267 — epic issue
- Consul service registry model — heartbeat/TTL design reference
