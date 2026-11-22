package io.casehub.platform.coordination.protocol;

import io.casehub.yaml.core.resolver.TemplateRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProtocolEvaluator {

    private final ConcurrentMap<String, TemplateRegistry> protocols = new ConcurrentHashMap<>();

    public void register(ProtocolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        TemplateRegistry.Builder builder = TemplateRegistry.builder();
        definition.templates().forEach(builder::add);
        TemplateRegistry registry = builder.build();
        if (protocols.putIfAbsent(definition.name(), registry) != null) {
            throw new IllegalArgumentException(
                    "Protocol already registered: " + definition.name());
        }
    }

    public ProtocolMatchResult evaluate(ProtocolInstanceKey key, String response) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(response, "response");
        TemplateRegistry registry = protocols.get(key.protocolName());
        if (registry == null) {
            throw new IllegalArgumentException(
                    "Unknown protocol: " + key.protocolName());
        }
        Optional<TemplateRegistry.Match> match = registry.match(response);
        return match.<ProtocolMatchResult>map(m ->
                        new ProtocolMatchResult.Matched(m.name(), m.values()))
                .orElseGet(() -> new ProtocolMatchResult.Unmatched(response));
    }

    public void deregister(String protocolName) {
        protocols.remove(protocolName);
    }

    public boolean isRegistered(String protocolName) {
        return protocols.containsKey(protocolName);
    }
}
