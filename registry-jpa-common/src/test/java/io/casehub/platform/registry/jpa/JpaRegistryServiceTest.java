package io.casehub.platform.registry.jpa;

import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaRegistryServiceTest {

    private EntityManager em;
    private JpaRegistryService service;

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        service = new JpaRegistryService(em, event -> {});
    }

    @Test
    void register_persistsOrphanedAt() {
        var orphanedAt = Instant.now().minusSeconds(300);
        var entry = new RegistryEntry(
                "svc-1", "service", "fleet", "tenant-1",
                Map.of(), Instant.now(), Instant.now(),
                Duration.ofSeconds(30), HealthStatus.HEALTHY, orphanedAt);

        when(em.find(eq(RegistryEntryEntity.class), eq("svc-1"))).thenReturn(null);
        when(em.merge(any(RegistryEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(entry);

        var captor = ArgumentCaptor.forClass(RegistryEntryEntity.class);
        verify(em).merge(captor.capture());
        assertThat(captor.getValue().orphanedAt).isEqualTo(orphanedAt);
    }

    @Test
    void register_preservesNullOrphanedAt() {
        var entry = new RegistryEntry(
                "svc-2", "service", "fleet", "tenant-1",
                Map.of(), Instant.now(), Instant.now(),
                Duration.ofSeconds(30), HealthStatus.HEALTHY, null);

        when(em.find(eq(RegistryEntryEntity.class), eq("svc-2"))).thenReturn(null);
        when(em.merge(any(RegistryEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(entry);

        var captor = ArgumentCaptor.forClass(RegistryEntryEntity.class);
        verify(em).merge(captor.capture());
        assertThat(captor.getValue().orphanedAt).isNull();
    }

    @Test
    void register_updatesOrphanedAtOnExistingEntry() {
        var existing = new RegistryEntryEntity();
        existing.id = "svc-3";
        existing.orphanedAt = Instant.now().minusSeconds(600);

        var entry = new RegistryEntry(
                "svc-3", "service", "fleet", "tenant-1",
                Map.of(), Instant.now(), Instant.now(),
                Duration.ofSeconds(30), HealthStatus.HEALTHY, null);

        when(em.find(eq(RegistryEntryEntity.class), eq("svc-3"))).thenReturn(existing);
        when(em.merge(any(RegistryEntryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(entry);

        var captor = ArgumentCaptor.forClass(RegistryEntryEntity.class);
        verify(em).merge(captor.capture());
        assertThat(captor.getValue().orphanedAt).isNull();
    }
}
