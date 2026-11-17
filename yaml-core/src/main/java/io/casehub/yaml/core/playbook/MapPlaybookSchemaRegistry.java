package io.casehub.yaml.core.playbook;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.casehub.yaml.core.playbook.PlaybookCapabilities.ARIA;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.CHAPTERS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.CODEGEN;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.CONCURRENCY;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.CONTROL_FLOW;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.CORRELATION;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.DECORATORS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.ENV_ACCESS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.EXPRESSION_MVEL;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.GRAPHQL_INVOKE;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.MCP_INVOKE;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.MODULES;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.ORCHESTRATION;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.PROCESS_EXECUTION;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.RAW_HTTP;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.SECTIONS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.SPOTLIGHT;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.STATE_MACHINE;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.STEPS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.TUTORIALS;
import static io.casehub.yaml.core.playbook.PlaybookCapabilities.VARIABLE_RESOLUTION;

public final class MapPlaybookSchemaRegistry implements PlaybookSchemaRegistry {

    private static final Set<String> SHARED_CAPABILITIES = Set.of(
            STEPS, VARIABLE_RESOLUTION, DECORATORS, CONTROL_FLOW,
            MODULES, CONCURRENCY, STATE_MACHINE
    );

    private final Map<String, PlaybookSchemaDescriptor> schemas = new ConcurrentHashMap<>();

    public MapPlaybookSchemaRegistry() {
        registerBuiltIns();
    }

    @Override
    public void register(PlaybookSchemaDescriptor descriptor) {
        schemas.put(descriptor.name(), descriptor);
    }

    @Override
    public Optional<PlaybookSchemaDescriptor> resolve(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName));
    }

    @Override
    public Collection<PlaybookSchemaDescriptor> all() {
        return schemas.values();
    }

    private void registerBuiltIns() {
        var clientCaps = new java.util.HashSet<>(SHARED_CAPABILITIES);
        clientCaps.addAll(Set.of(CHAPTERS, SECTIONS, ARIA, SPOTLIGHT, TUTORIALS,
                                 PROCESS_EXECUTION, RAW_HTTP, EXPRESSION_MVEL, ENV_ACCESS));
        register(PlaybookSchemaDescriptor.builtIn(PlaybookSchemas.CLIENT, clientCaps));

        var serverCaps = new java.util.HashSet<>(SHARED_CAPABILITIES);
        serverCaps.addAll(Set.of(ORCHESTRATION, CORRELATION, MCP_INVOKE, GRAPHQL_INVOKE, CODEGEN,
                                 PROCESS_EXECUTION, RAW_HTTP, EXPRESSION_MVEL, ENV_ACCESS));
        register(PlaybookSchemaDescriptor.builtIn(PlaybookSchemas.SERVER, serverCaps));

        var assistedCaps = new java.util.HashSet<>(SHARED_CAPABILITIES);
        assistedCaps.addAll(Set.of(ORCHESTRATION, CORRELATION, MCP_INVOKE, GRAPHQL_INVOKE, CODEGEN));
        register(PlaybookSchemaDescriptor.builtIn(PlaybookSchemas.ASSISTED, assistedCaps));
    }
}
