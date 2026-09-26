package io.casehub.yaml.step.catalog;

import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.step.CatalogEntry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpStepCatalogWiringTest {

    private DomainModelRegistry registry;
    private McpStepCatalogWiring wiring;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new DomainModelRegistry();

        Instance<DomainModelRegistry> registryInstance = mock(Instance.class);
        when(registryInstance.isResolvable()).thenReturn(true);
        when(registryInstance.get()).thenReturn(registry);

        Instance<Object> dispatcherInstance = mock(Instance.class);
        when(dispatcherInstance.isResolvable()).thenReturn(false);

        wiring = new McpStepCatalogWiring();
        wiring.registryInstance = registryInstance;
        wiring.dispatcherInstance = dispatcherInstance;
    }

    @Test
    void populatesToolsFromDomainRegistry() {
        registry.register(new DomainModel("acl", null, "Access control", List.of(
                new OperationDescriptor("canAccess", OperationDescriptor.OperationType.QUERY,
                        "Check access",
                        List.of(new ParameterDescriptor("actorId", "String", true, "Actor ID", Map.of()),
                                new ParameterDescriptor("resourceId", "String", true, "Resource ID", Map.of())),
                        "Boolean", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        wiring.populate(entries);

        assertThat(entries).containsKey("acl_canAccess");
        var def = entries.get("acl_canAccess").definition();
        assertThat(def.inputs()).containsKey("actorId");
        assertThat(def.inputs()).containsKey("resourceId");
        assertThat(def.inputs().get("actorId").type()).isEqualTo(StepParameterType.STRING);
        assertThat(def.inputs().get("actorId").required()).isTrue();
    }

    @Test
    void skipsStreamOperations() {
        registry.register(new DomainModel("events", null, "Events", List.of(
                new OperationDescriptor("subscribe", OperationDescriptor.OperationType.STREAM,
                        "Subscribe", List.of(), "Publisher", null, null),
                new OperationDescriptor("list", OperationDescriptor.OperationType.QUERY,
                        "List events", List.of(), "List", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        wiring.populate(entries);

        assertThat(entries).containsKey("events_list");
        assertThat(entries).doesNotContainKey("events_subscribe");
    }

    @Test
    void mapsParameterTypes() {
        registry.register(new DomainModel("test", null, null, List.of(
                new OperationDescriptor("op", OperationDescriptor.OperationType.MUTATION, null,
                        List.of(new ParameterDescriptor("count", "Integer", false, null, Map.of()),
                                new ParameterDescriptor("ratio", "Double", false, null, Map.of()),
                                new ParameterDescriptor("active", "Boolean", false, null, Map.of()),
                                new ParameterDescriptor("tags", "List<String>", false, null, Map.of()),
                                new ParameterDescriptor("meta", "CustomType", false, null, Map.of())),
                        "void", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        wiring.populate(entries);

        var inputs = entries.get("test_op").definition().inputs();
        assertThat(inputs.get("count").type()).isEqualTo(StepParameterType.INTEGER);
        assertThat(inputs.get("ratio").type()).isEqualTo(StepParameterType.NUMBER);
        assertThat(inputs.get("active").type()).isEqualTo(StepParameterType.BOOLEAN);
        assertThat(inputs.get("tags").type()).isEqualTo(StepParameterType.ARRAY);
        assertThat(inputs.get("meta").type()).isEqualTo(StepParameterType.OBJECT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inertWhenRegistryNotResolvable() {
        Instance<DomainModelRegistry> absent = mock(Instance.class);
        when(absent.isResolvable()).thenReturn(false);
        wiring.registryInstance = absent;

        wiring.onScanComplete(new ModelScanComplete());

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        wiring.populate(entries);

        assertThat(entries).isEmpty();
    }

    @Test
    void populateIsNoOpBeforeScanComplete() {
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        wiring.populate(entries);

        assertThat(entries).isEmpty();
    }

    @Test
    void priorityIs300() {
        assertThat(wiring.priority()).isEqualTo(300);
    }

    @Test
    void mapsNullTypeNameToString() {
        assertThat(McpStepCatalogWiring.mapType(null)).isEqualTo(StepParameterType.STRING);
    }

    @Test
    void mapsCollectionToArray() {
        assertThat(McpStepCatalogWiring.mapType("Collection<Foo>")).isEqualTo(StepParameterType.ARRAY);
    }

    @Test
    void mapsPrimitiveTypes() {
        assertThat(McpStepCatalogWiring.mapType("int")).isEqualTo(StepParameterType.INTEGER);
        assertThat(McpStepCatalogWiring.mapType("long")).isEqualTo(StepParameterType.INTEGER);
        assertThat(McpStepCatalogWiring.mapType("double")).isEqualTo(StepParameterType.NUMBER);
        assertThat(McpStepCatalogWiring.mapType("float")).isEqualTo(StepParameterType.NUMBER);
        assertThat(McpStepCatalogWiring.mapType("boolean")).isEqualTo(StepParameterType.BOOLEAN);
    }
}
