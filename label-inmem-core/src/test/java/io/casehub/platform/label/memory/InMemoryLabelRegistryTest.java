package io.casehub.platform.label.memory;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.label.LabelDeregistered;
import io.casehub.platform.api.label.LabelRegistered;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLabelRegistryTest {

    private static final String TENANT = "tenant-1";
    private static final String PLATFORM = TenancyConstants.PLATFORM_TENANT_ID;

    private final List<LabelRegistered> registeredEvents = new ArrayList<>();
    private final List<LabelDeregistered> deregisteredEvents = new ArrayList<>();
    private InMemoryLabelRegistry registry;

    @BeforeEach
    void setUp() {
        registeredEvents.clear();
        deregisteredEvents.clear();
        registry = new InMemoryLabelRegistry(registeredEvents::add, deregisteredEvents::add);
    }

    @Test
    void registerAndResolve() {
        var path = Path.of("IT", "Hardware");
        var descriptor = new LabelDescriptor(TENANT, path, "Hardware", 1);

        registry.register(TENANT, descriptor);

        assertThat(registry.resolve(TENANT, path)).contains(descriptor);
    }

    @Test
    void resolveReturnsTenantOverPlatform() {
        var path = Path.of("IT", "Hardware");
        var global = new LabelDescriptor(PLATFORM, path, "Hardware (Global)", 0);
        var tenant = new LabelDescriptor(TENANT, path, "Hardware (Tenant)", 1);

        registry.register(PLATFORM, global);
        registry.register(TENANT, tenant);

        assertThat(registry.resolve(TENANT, path)).contains(tenant);
    }

    @Test
    void resolveFallsThroughToPlatform() {
        var path = Path.of("IT", "Hardware");
        var global = new LabelDescriptor(PLATFORM, path, "Hardware", 0);

        registry.register(PLATFORM, global);

        assertThat(registry.resolve(TENANT, path)).contains(global);
    }

    @Test
    void resolveReturnsEmptyWhenNotFound() {
        assertThat(registry.resolve(TENANT, Path.of("Nonexistent"))).isEmpty();
    }

    @Test
    void childrenMergesPlatformAndTenant() {
        var root = Path.root();
        var globalIt = new LabelDescriptor(PLATFORM, Path.of("IT"), "IT", 0);
        var tenantHr = new LabelDescriptor(TENANT, Path.of("HR"), "HR", 1);

        registry.register(PLATFORM, globalIt);
        registry.register(TENANT, tenantHr);

        var children = registry.children(TENANT, root);
        assertThat(children).extracting(LabelDescriptor::displayName)
                .containsExactly("IT", "HR");
    }

    @Test
    void childrenTenantOverridesPlatform() {
        var root = Path.root();
        var global = new LabelDescriptor(PLATFORM, Path.of("IT"), "IT (Global)", 0);
        var tenant = new LabelDescriptor(TENANT, Path.of("IT"), "IT (Tenant)", 0);

        registry.register(PLATFORM, global);
        registry.register(TENANT, tenant);

        var children = registry.children(TENANT, root);
        assertThat(children).hasSize(1);
        assertThat(children.get(0).displayName()).isEqualTo("IT (Tenant)");
    }

    @Test
    void childrenOrderedBySortOrderThenDisplayName() {
        var root = Path.root();
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("Zebra"), "Zebra", 2));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("Alpha"), "Alpha", 1));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("Beta"), "Beta", 1));

        var children = registry.children(TENANT, root);
        assertThat(children).extracting(LabelDescriptor::displayName)
                .containsExactly("Alpha", "Beta", "Zebra");
    }

    @Test
    void ancestorsAutoCreated() {
        var leaf = Path.of("IT", "Hardware", "Laptops");
        registry.register(TENANT, new LabelDescriptor(TENANT, leaf, "Laptops", 0));

        assertThat(registry.resolve(TENANT, Path.of("IT"))).isPresent();
        assertThat(registry.resolve(TENANT, Path.of("IT", "Hardware"))).isPresent();
    }

    @Test
    void ancestorsDefaultDisplayNameFromSegment() {
        var leaf = Path.of("IT", "Hardware", "Laptops");
        registry.register(TENANT, new LabelDescriptor(TENANT, leaf, "Laptops", 0));

        var it = registry.resolve(TENANT, Path.of("IT")).orElseThrow();
        assertThat(it.displayName()).isEqualTo("IT");

        var hw = registry.resolve(TENANT, Path.of("IT", "Hardware")).orElseThrow();
        assertThat(hw.displayName()).isEqualTo("Hardware");
    }

    @Test
    void explicitRegistrationOverridesAutoCreatedAncestor() {
        var leaf = Path.of("IT", "Hardware", "Laptops");
        registry.register(TENANT, new LabelDescriptor(TENANT, leaf, "Laptops", 0));

        var explicit = new LabelDescriptor(TENANT, Path.of("IT"), "Information Technology", 5);
        registry.register(TENANT, explicit);

        var it = registry.resolve(TENANT, Path.of("IT")).orElseThrow();
        assertThat(it.displayName()).isEqualTo("Information Technology");
        assertThat(it.sortOrder()).isEqualTo(5);
    }

    @Test
    void deregisterRemovesExactMatch() {
        var path = Path.of("IT");
        registry.register(TENANT, new LabelDescriptor(TENANT, path, "IT", 0));

        registry.deregister(TENANT, path);

        assertThat(registry.resolve(TENANT, path)).isEmpty();
    }

    @Test
    void deregisterDoesNotAffectOtherTenants() {
        var path = Path.of("IT");
        registry.register(PLATFORM, new LabelDescriptor(PLATFORM, path, "IT", 0));
        registry.register(TENANT, new LabelDescriptor(TENANT, path, "IT", 0));

        registry.deregister(TENANT, path);

        assertThat(registry.resolve(TENANT, path)).isPresent();
        assertThat(registry.resolve(TENANT, path).get().tenancyId()).isEqualTo(PLATFORM);
    }

    @Test
    void subtreeReturnsAllDescendants() {
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Hardware", "Laptops"), "Laptops", 0));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Hardware", "Monitors"), "Monitors", 1));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Software"), "Software", 2));

        var subtree = registry.subtree(TENANT, Path.of("IT"));
        assertThat(subtree).extracting(d -> d.path().value())
                .containsExactly("IT/Hardware", "IT/Hardware/Laptops", "IT/Hardware/Monitors", "IT/Software");
    }

    @Test
    void subtreeFromRootReturnsEverything() {
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT"), "IT", 0));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("HR"), "HR", 1));

        var subtree = registry.subtree(TENANT, Path.root());
        assertThat(subtree).extracting(d -> d.path().value())
                .containsExactly("HR", "IT");
    }

    @Test
    void subtreeMergesPlatformAndTenant() {
        registry.register(PLATFORM, new LabelDescriptor(PLATFORM, Path.of("IT", "Hardware"), "Hardware", 0));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Software"), "Software", 1));

        var subtree = registry.subtree(TENANT, Path.of("IT"));
        assertThat(subtree).extracting(LabelDescriptor::displayName)
                .containsExactly("Hardware", "Software");
    }

    @Test
    void registerFiresEvent() {
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT"), "IT", 0));

        assertThat(registeredEvents).isNotEmpty();
        assertThat(registeredEvents.get(registeredEvents.size() - 1).descriptor().displayName()).isEqualTo("IT");
    }

    @Test
    void metadataIsPreserved() {
        var descriptor = new LabelDescriptor(TENANT, Path.of("IT"), "IT", 0,
                Map.of("icon", "computer", "color", "blue"));

        registry.register(TENANT, descriptor);

        var resolved = registry.resolve(TENANT, Path.of("IT")).orElseThrow();
        assertThat(resolved.metadata()).containsEntry("icon", "computer");
        assertThat(resolved.metadata()).containsEntry("color", "blue");
    }

    @Test
    void deregisterFiresEvent() {
        var path = Path.of("IT");
        registry.register(TENANT, new LabelDescriptor(TENANT, path, "IT", 0));

        registry.deregister(TENANT, path);

        assertThat(deregisteredEvents).hasSize(1);
        assertThat(deregisteredEvents.get(0).tenancyId()).isEqualTo(TENANT);
        assertThat(deregisteredEvents.get(0).path()).isEqualTo(path);
    }

    @Test
    void deregisterNoOpDoesNotFireEvent() {
        registry.deregister(TENANT, Path.of("Nonexistent"));

        assertThat(deregisteredEvents).isEmpty();
    }

    @Test
    void registerRejectsMismatchedTenancyId() {
        var descriptor = new LabelDescriptor("tenant-A", Path.of("IT"), "IT", 0);

        assertThatThrownBy(() -> registry.register("tenant-B", descriptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant-A")
                .hasMessageContaining("tenant-B");
    }
}
