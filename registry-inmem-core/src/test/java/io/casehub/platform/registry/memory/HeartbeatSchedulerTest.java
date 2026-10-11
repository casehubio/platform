package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.CascadeAction;
import io.casehub.platform.api.registry.CascadeRule;
import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryEvent;
import io.casehub.platform.api.registry.Relationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatSchedulerTest {

    private InMemoryRegistryService registry;
    private HeartbeatScheduler scheduler;
    private final ArrayList<RegistryEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryService(events::add);
        scheduler = new HeartbeatScheduler(registry, events::add);
        events.clear();
    }

    private RegistryEntry entryWithTtl(String id, Duration ttl, Instant lastHeartbeat) {
        return new RegistryEntry(id, "service", "default", "tenant-1",
                Map.of(), Instant.now(), lastHeartbeat, ttl, HealthStatus.HEALTHY, null);
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
    void expiredEntryFiresHeartbeatExpiredEvent() {
        var entry = entryWithTtl("svc-1", Duration.ofSeconds(1),
                Instant.now().minus(Duration.ofSeconds(5)));
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(events).anyMatch(e ->
                e.kind() == RegistryEvent.EventKind.HEARTBEAT_EXPIRED
                        && e.entry().id().equals("svc-1"));
    }

    @Test
    void nullHeartbeatUsesRegisteredAt() {
        var longAgo = Instant.now().minus(Duration.ofSeconds(60));
        var entry = new RegistryEntry("svc-1", "service", "default", "tenant-1",
                Map.of(), longAgo, null, Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
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

    @Test
    void multipleEntriesOnlyExpiredOnesMarkedDown() {
        registry.register(entryWithTtl("svc-alive", Duration.ofSeconds(30), Instant.now()));
        registry.register(entryWithTtl("svc-dead", Duration.ofSeconds(1),
                Instant.now().minus(Duration.ofSeconds(10))));
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-alive").get().health()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(registry.resolve("svc-dead").get().health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void crossTenantScan() {
        var t1 = new RegistryEntry("svc-t1", "service", "default", "tenant-1",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var t2 = new RegistryEntry("svc-t2", "service", "default", "tenant-2",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        registry.register(t1);
        registry.register(t2);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-t1").get().health()).isEqualTo(HealthStatus.DOWN);
        assertThat(registry.resolve("svc-t2").get().health()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void degradedEntryMarkedDownWhenExpired() {
        var entry = new RegistryEntry("svc-1", "service", "default", "tenant-1",
                                      Map.of(), Instant.now(), Instant.now().minus(Duration.ofSeconds(10)),
                                      Duration.ofSeconds(5), HealthStatus.DEGRADED, null);
        registry.register(entry);
        events.clear();
        scheduler.checkHeartbeats();
        assertThat(registry.resolve("svc-1").get().health()).isEqualTo(HealthStatus.DOWN);
    }

    // --- TTL cascade ---

    @Test
    void ttlExpiry_cascadesDeregisterWithNoGracePeriod() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = new RegistryEntry("tgt", "pool", "ns", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(300),
                HealthStatus.HEALTHY, null);
        registry.register(source);
        registry.register(target);
        registry.link(new Relationship("src", "tgt", "owns"));
        registry.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.DEREGISTER, null));

        scheduler.checkHeartbeats();

        assertThat(registry.resolve("tgt")).isEmpty();
    }

    @Test
    void ttlExpiry_respectsGracePeriod() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = new RegistryEntry("tgt", "pool", "ns", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(300),
                HealthStatus.HEALTHY, null);
        registry.register(source);
        registry.register(target);
        registry.link(new Relationship("src", "tgt", "owns"));
        registry.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.DEREGISTER,
                Duration.ofMinutes(5)));

        scheduler.checkHeartbeats();

        assertThat(registry.resolve("src").get().health()).isEqualTo(HealthStatus.DOWN);
        assertThat(registry.resolve("tgt")).isPresent();
    }

    @Test
    void ttlExpiry_usesOnSourceExpiredAction() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = new RegistryEntry("tgt", "pool", "ns", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(300),
                HealthStatus.HEALTHY, null);
        registry.register(source);
        registry.register(target);
        registry.link(new Relationship("src", "tgt", "owns"));
        registry.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.MARK_ORPHANED, null));
        events.clear();

        scheduler.checkHeartbeats();

        assertThat(registry.resolve("tgt")).isPresent()
                .hasValueSatisfying(e -> assertThat(e.isOrphaned()).isTrue());
    }

    @Test
    void ttlExpiry_markOrphanedIdempotentOnSubsequentTick() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minus(Duration.ofSeconds(60)), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = new RegistryEntry("tgt", "pool", "ns", "tenant-1",
                Map.of(), Instant.now(), null, Duration.ofSeconds(300),
                HealthStatus.HEALTHY, null);
        registry.register(source);
        registry.register(target);
        registry.link(new Relationship("src", "tgt", "owns"));
        registry.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.MARK_ORPHANED, null));

        scheduler.checkHeartbeats();
        var firstOrphanedAt = registry.resolve("tgt").get().orphanedAt();
        assertThat(firstOrphanedAt).isNotNull();

        scheduler.checkHeartbeats();
        var secondOrphanedAt = registry.resolve("tgt").get().orphanedAt();
        assertThat(secondOrphanedAt).isEqualTo(firstOrphanedAt);
    }
}
