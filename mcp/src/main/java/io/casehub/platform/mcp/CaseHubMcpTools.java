package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@McpServer("casehub")
@WrapBusinessError({IllegalArgumentException.class, IllegalStateException.class})
@ApplicationScoped
public class CaseHubMcpTools {

    @Inject
    ModelRegistry registry;

    private final ObjectMapper mapper;

    public CaseHubMcpTools() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Tool(description = "Navigate the CaseHub operation catalog. "
            + "Call without domain for a domain list. "
            + "Call with domain for operation details.")
    public String casehub_model(
            @ToolArg(description = "Domain name to drill into (omit for domain list)")
            String domain) throws JsonProcessingException {
        if (domain == null || domain.isBlank()) {
            return mapper.writeValueAsString(buildTier0());
        }
        return mapper.writeValueAsString(buildTier1(domain));
    }

    private Map<String, Object> buildTier0() {
        List<Map<String, Object>> domainList = registry.getDomains().stream()
                .map(d -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", d.name());
                    if (!d.summary().isEmpty()) entry.put("summary", d.summary());
                    entry.put("operationCount", d.operations().size());
                    if (!d.events().isEmpty()) entry.put("eventCount", d.events().size());
                    if (!d.state().isEmpty()) entry.put("state", d.state());
                    return entry;
                })
                .collect(Collectors.toList());
        return Map.of("domains", domainList);
    }

    private Map<String, Object> buildTier1(String domainName) {
        DomainModel domain = registry.getDomain(domainName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown domain: " + domainName));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain.name());
        if (!domain.summary().isEmpty()) result.put("summary", domain.summary());
        if (!domain.state().isEmpty()) result.put("state", domain.state());

        List<Map<String, Object>> queries = domain.operations().stream()
                .filter(op -> op.type() == OperationDescriptor.OperationType.QUERY)
                .map(this::operationToMap)
                .collect(Collectors.toList());
        if (!queries.isEmpty()) result.put("queries", queries);

        List<Map<String, Object>> mutations = domain.operations().stream()
                .filter(op -> op.type() == OperationDescriptor.OperationType.MUTATION)
                .map(this::operationToMap)
                .collect(Collectors.toList());
        if (!mutations.isEmpty()) result.put("mutations", mutations);

        List<Map<String, Object>> events = domain.events().stream()
                .map(this::eventToMap)
                .collect(Collectors.toList());
        if (!events.isEmpty()) result.put("events", events);

        return result;
    }

    private Map<String, Object> operationToMap(OperationDescriptor op) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", op.name());
        if (!op.summary().isEmpty()) map.put("summary", op.summary());
        if (!op.params().isEmpty()) {
            map.put("params", op.params().stream()
                    .map(this::paramToMap).collect(Collectors.toList()));
        }
        map.put("returns", op.returnTypeName());
        return map;
    }

    private Map<String, Object> eventToMap(EventDescriptor event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", event.name());
        if (!event.summary().isEmpty()) map.put("summary", event.summary());
        if (!event.params().isEmpty()) {
            map.put("params", event.params().stream()
                    .map(this::paramToMap).collect(Collectors.toList()));
        }
        map.put("delivery", "qhorus");
        map.put("channel", event.channel());
        return map;
    }

    private Map<String, Object> paramToMap(ParameterDescriptor param) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", param.name());
        map.put("type", param.typeName());
        if (param.required()) map.put("required", true);
        if (!param.description().isEmpty()) map.put("description", param.description());
        if (!param.fields().isEmpty()) map.put("fields", param.fields());
        return map;
    }
}
