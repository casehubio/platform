package io.casehub.platform.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

<<<<<<<< HEAD:mcp/src/main/java/io/casehub/platform/mcp/DomainModelRegistry.java
@ApplicationScoped
public class DomainModelRegistry {
========
public class ModelRegistry {
>>>>>>>> 813062bd (feat(#276): extract framework-neutral core from platform modules):mcp-core/src/main/java/io/casehub/platform/mcp/ModelRegistry.java

    private final Map<String, DomainModel> domains = new ConcurrentHashMap<>();

    public void register(DomainModel model) {
        domains.put(model.name(), model);
    }

    public List<DomainModel> getDomains() {
        return List.copyOf(domains.values());
    }

    public Optional<DomainModel> getDomain(String name) {
        return Optional.ofNullable(domains.get(name));
    }

    public Optional<OperationDescriptor> getOperation(String domain, String operation) {
        return getDomain(domain)
                .flatMap(d -> d.operations().stream()
                        .filter(op -> op.name().equals(operation))
                        .findFirst());
    }
}
