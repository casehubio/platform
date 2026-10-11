package io.casehub.platform.registry.jpa;

import io.casehub.platform.api.registry.CascadeAction;
import io.casehub.platform.api.registry.CascadeRule;
import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryEvent;
import io.casehub.platform.api.registry.Relationship;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JpaRegistryServiceCascadeTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private JpaRegistryService service;
    private final ArrayList<RegistryEvent> events = new ArrayList<>();

    @BeforeAll
    static void initEmf() {
        emf = Persistence.createEntityManagerFactory("registry-test");
    }

    @AfterAll
    static void closeEmf() {
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
        service = new JpaRegistryService(em, events::add);
        events.clear();
        em.getTransaction().begin();
    }

    @AfterEach
    void tearDown() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }

    @Test
    void deregister_cascadesDeregisterToTarget() {
        var source = entry("src", HealthStatus.HEALTHY, null);
        var target = entry("tgt", HealthStatus.HEALTHY, null);
        service.register(source);
        service.register(target);
        service.link(new Relationship("src", "tgt", "owns"));
        service.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.DEREGISTER, null));

        var result = service.deregister("src");

        assertThat(service.resolve("tgt")).isEmpty();
        assertThat(result.cascadeEffects()).hasSize(1);
        assertThat(result.cascadeEffects().get(0).action()).isEqualTo(CascadeAction.DEREGISTER);
        assertThat(result.cascadeEffects().get(0).targetId()).isEqualTo("tgt");
    }

    @Test
    void deregister_cascadesMarkOrphanedToTarget() {
        var source = entry("src", HealthStatus.HEALTHY, null);
        var target = entry("tgt", HealthStatus.HEALTHY, null);
        service.register(source);
        service.register(target);
        service.link(new Relationship("src", "tgt", "owns"));
        service.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.MARK_ORPHANED, CascadeAction.MARK_ORPHANED, null));

        service.deregister("src");

        assertThat(service.resolve("tgt")).isPresent()
                .hasValueSatisfying(e -> assertThat(e.isOrphaned()).isTrue());
    }

    @Test
    void checkHeartbeats_cascadesOnExpiry() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minusSeconds(60), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = entry("tgt", HealthStatus.HEALTHY, null);
        service.register(source);
        service.register(target);
        service.link(new Relationship("src", "tgt", "owns"));
        service.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.DEREGISTER, null));
        em.flush();
        events.clear();

        service.checkHeartbeats();

        assertThat(service.resolve("src")).isPresent()
                .hasValueSatisfying(e -> assertThat(e.health()).isEqualTo(HealthStatus.DOWN));
        assertThat(service.resolve("tgt")).isEmpty();
    }

    @Test
    void checkHeartbeats_respectsGracePeriod() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minusSeconds(60), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = entry("tgt", HealthStatus.HEALTHY, null);
        service.register(source);
        service.register(target);
        service.link(new Relationship("src", "tgt", "owns"));
        service.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.DEREGISTER,
                Duration.ofMinutes(5)));
        em.flush();
        events.clear();

        service.checkHeartbeats();

        assertThat(service.resolve("src")).isPresent()
                .hasValueSatisfying(e -> assertThat(e.health()).isEqualTo(HealthStatus.DOWN));
        assertThat(service.resolve("tgt")).isPresent();
    }

    @Test
    void checkHeartbeats_markOrphanedIdempotent() {
        var source = new RegistryEntry("src", "app", "ns", "tenant-1",
                Map.of(), Instant.now().minusSeconds(60), null,
                Duration.ofSeconds(5), HealthStatus.HEALTHY, null);
        var target = entry("tgt", HealthStatus.HEALTHY, null);
        service.register(source);
        service.register(target);
        service.link(new Relationship("src", "tgt", "owns"));
        service.registerCascadeRule(new CascadeRule("owns",
                CascadeAction.DEREGISTER, CascadeAction.MARK_ORPHANED, null));
        em.flush();

        service.checkHeartbeats();
        em.flush();
        var firstOrphanedAt = service.resolve("tgt").get().orphanedAt();
        assertThat(firstOrphanedAt).isNotNull();

        service.checkHeartbeats();
        em.flush();
        var secondOrphanedAt = service.resolve("tgt").get().orphanedAt();
        assertThat(secondOrphanedAt).isEqualTo(firstOrphanedAt);
    }

    private RegistryEntry entry(String id, HealthStatus health, Instant orphanedAt) {
        return new RegistryEntry(id, "service", "default", "tenant-1",
                Map.of(), Instant.now(), Instant.now(),
                Duration.ofSeconds(300), health, orphanedAt);
    }
}
