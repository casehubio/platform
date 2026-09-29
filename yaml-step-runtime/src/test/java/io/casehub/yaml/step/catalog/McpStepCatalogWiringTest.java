package io.casehub.yaml.step.catalog;

import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpStepCatalogWiringTest {

    private DomainModelRegistry domainRegistry;
    private McpStepCatalogWiring wiring;
    private CompositePluginRegistry pluginRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        domainRegistry = new DomainModelRegistry();
        pluginRegistry = new CompositePluginRegistry();

        Instance<DomainModelRegistry> registryInstance = mock(Instance.class);
        when(registryInstance.isResolvable()).thenReturn(true);
        when(registryInstance.get()).thenReturn(domainRegistry);

        Instance<io.casehub.platform.api.mcp.ToolDispatcher> dispatcherInstance = mock(Instance.class);
        when(dispatcherInstance.isResolvable()).thenReturn(false);

        Instance<PluginRegistry> pluginRegistryInst = mock(Instance.class);
        when(pluginRegistryInst.isResolvable()).thenReturn(true);
        when(pluginRegistryInst.get()).thenReturn(pluginRegistry);

        wiring = new McpStepCatalogWiring();
        wiring.registryInstance = registryInstance;
        wiring.dispatcherInstance = dispatcherInstance;
        wiring.pluginRegistryInstance = pluginRegistryInst;
    }

    @Test
    void populatesToolsFromDomainRegistry() {
        domainRegistry.register(new DomainModel("acl", null, "Access control", List.of(
                new OperationDescriptor("canAccess", OperationDescriptor.OperationType.QUERY,
                        "Check access",
                        List.of(new ParameterDescriptor("actorId", "String", true, "Actor ID", Map.of()),
                                new ParameterDescriptor("resourceId", "String", true, "Resource ID", Map.of())),
                        "Boolean", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        assertThat(pluginRegistry.resolve("acl_canAccess")).isPresent();
        var def = pluginRegistry.resolve("acl_canAccess").get();
        assertThat(def.inputs()).containsKey("actorId");
        assertThat(def.inputs()).containsKey("resourceId");
        assertThat(def.inputs().get("actorId").type()).isEqualTo(ParameterType.STRING);
        assertThat(def.inputs().get("actorId").required()).isTrue();
    }

    @Test
    void skipsStreamOperations() {
        domainRegistry.register(new DomainModel("events", null, "Events", List.of(
                new OperationDescriptor("subscribe", OperationDescriptor.OperationType.STREAM,
                        "Subscribe", List.of(), "Publisher", null, null),
                new OperationDescriptor("list", OperationDescriptor.OperationType.QUERY,
                        "List events", List.of(), "List", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        assertThat(pluginRegistry.resolve("events_list")).isPresent();
        assertThat(pluginRegistry.resolve("events_subscribe")).isEmpty();
    }

    @Test
    void mapsParameterTypes() {
        domainRegistry.register(new DomainModel("test", null, null, List.of(
                new OperationDescriptor("op", OperationDescriptor.OperationType.MUTATION, null,
                        List.of(new ParameterDescriptor("count", "Integer", false, null, Map.of()),
                                new ParameterDescriptor("ratio", "Double", false, null, Map.of()),
                                new ParameterDescriptor("active", "Boolean", false, null, Map.of()),
                                new ParameterDescriptor("tags", "List<String>", false, null, Map.of()),
                                new ParameterDescriptor("meta", "CustomType", false, null, Map.of())),
                        "void", null, null)
        ), List.of(), Map.of()));

        wiring.onScanComplete(new ModelScanComplete());

        var def = pluginRegistry.resolve("test_op").get();
        assertThat(def.inputs().get("count").type()).isEqualTo(ParameterType.INTEGER);
        assertThat(def.inputs().get("ratio").type()).isEqualTo(ParameterType.NUMBER);
        assertThat(def.inputs().get("active").type()).isEqualTo(ParameterType.BOOLEAN);
        assertThat(def.inputs().get("tags").type()).isEqualTo(ParameterType.ARRAY);
        assertThat(def.inputs().get("meta").type()).isEqualTo(ParameterType.OBJECT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inertWhenRegistryNotResolvable() {
        Instance<DomainModelRegistry> absent = mock(Instance.class);
        when(absent.isResolvable()).thenReturn(false);
        wiring.registryInstance = absent;

        wiring.onScanComplete(new ModelScanComplete());

        assertThat(pluginRegistry.availableActions()).isEmpty();
    }

    @Test
    void mapsNullTypeNameToString() {
        assertThat(McpStepCatalogWiring.mapType(null)).isEqualTo(ParameterType.STRING);
    }

    @Test
    void mapsCollectionToArray() {
        assertThat(McpStepCatalogWiring.mapType("Collection<Foo>")).isEqualTo(ParameterType.ARRAY);
    }

    @Test
    void mapsPrimitiveTypes() {
        assertThat(McpStepCatalogWiring.mapType("int")).isEqualTo(ParameterType.INTEGER);
        assertThat(McpStepCatalogWiring.mapType("long")).isEqualTo(ParameterType.INTEGER);
        assertThat(McpStepCatalogWiring.mapType("double")).isEqualTo(ParameterType.NUMBER);
        assertThat(McpStepCatalogWiring.mapType("float")).isEqualTo(ParameterType.NUMBER);
        assertThat(McpStepCatalogWiring.mapType("boolean")).isEqualTo(ParameterType.BOOLEAN);
    }
}
