package io.casehub.platform.mcp;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DomainModelRegistry {

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

    public List<String> getApps() {
        return domains.values().stream()
                      .map(DomainModel::app)
                      .distinct()
                      .sorted()
                      .toList();
    }

    public List<DomainModel> getDomainsByApp(String app) {
        return domains.values().stream()
                      .filter(d -> app.equals(d.app()))
                      .toList();
    }


    public Optional<OperationDescriptor> getOperation(String domain, String operation) {
        return getDomain(domain)
                .flatMap(d -> d.operations().stream()
                        .filter(op -> op.name().equals(operation))
                        .findFirst());
    }

    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String             lowerQuery = query.toLowerCase(java.util.Locale.ROOT);
        List<SearchResult> results    = new java.util.ArrayList<>();
        for (DomainModel domain : domains.values()) {
            for (OperationDescriptor op : domain.operations()) {
                if (matches(op, lowerQuery)) {
                    results.add(new SearchResult(domain.name(), op));
                }
            }
        }
        return List.copyOf(results);
    }

    private static boolean matches(OperationDescriptor op, String lowerQuery) {
        if (op.name().toLowerCase(java.util.Locale.ROOT).contains(lowerQuery)) {return true;}
        if (op.summary() != null && op.summary().toLowerCase(java.util.Locale.ROOT).contains(lowerQuery)) {return true;}
        if (op.returnTypeName() != null && op.returnTypeName().toLowerCase(java.util.Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        for (ParameterDescriptor param : op.params()) {
            if (param.name().toLowerCase(java.util.Locale.ROOT).contains(lowerQuery)) {return true;}
        }
        return false;
    }

}
