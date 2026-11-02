package io.casehub.platform.label;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.label.memory.InMemoryLabelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LabelServiceCoreTest {

    private static final String TENANT = "tenant-1";

    private InMemoryLabelRegistry registry;
    private LabelServiceCore service;

    @BeforeEach
    void setUp() {
        registry = new InMemoryLabelRegistry();
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return "user-1"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
        service = new LabelServiceCore(registry, principal);

        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Hardware", "Laptops"), "Laptops", 0));
        registry.register(TENANT, new LabelDescriptor(TENANT, Path.of("IT", "Software"), "Software", 1));
    }

    @Test
    void childrenWithEmptyStringReturnsTopLevel() {
        var children = service.children("");
        assertThat(children).extracting(LabelDescriptor::displayName).contains("IT");
    }

    @Test
    void childrenWithNullReturnsTopLevel() {
        var children = service.children(null);
        assertThat(children).extracting(LabelDescriptor::displayName).contains("IT");
    }

    @Test
    void childrenWithPathReturnsDirectChildren() {
        var children = service.children("IT");
        assertThat(children).extracting(LabelDescriptor::displayName)
                .containsExactly("Hardware", "Software");
    }

    @Test
    void resolveExistingPath() {
        var result = service.resolve("IT/Hardware/Laptops");
        assertThat(result).isPresent();
        assertThat(result.get().displayName()).isEqualTo("Laptops");
    }

    @Test
    void resolveNonexistentPath() {
        assertThat(service.resolve("Nonexistent")).isEmpty();
    }

    @Test
    void resolveNullReturnsEmpty() {
        assertThat(service.resolve(null)).isEmpty();
    }

    @Test
    void resolveBlankReturnsEmpty() {
        assertThat(service.resolve("  ")).isEmpty();
    }

    @Test
    void subtreeWithEmptyStringReturnsAll() {
        var subtree = service.subtree("");
        assertThat(subtree).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void subtreeWithPathReturnsDescendants() {
        var subtree = service.subtree("IT/Hardware");
        assertThat(subtree).extracting(LabelDescriptor::displayName)
                .containsExactly("Laptops");
    }
}
