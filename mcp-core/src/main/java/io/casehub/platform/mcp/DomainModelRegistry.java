package io.casehub.platform.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DomainModelRegistry {

    private final Map<String, DomainModel> domains = new ConcurrentHashMap<>();
    private final Map<String, DomainContentFormatter.AppCapability> appCapabilities = new ConcurrentHashMap<>();

    public void register(DomainModel model) {
        domains.put(model.name(), model);
    }

    public void registerAppCapability(String app, DomainContentFormatter.AppCapability capability) {
        appCapabilities.put(app, capability);
    }

    public Map<String, DomainContentFormatter.AppCapability> getAppCapabilities() {
        return Map.copyOf(appCapabilities);
    }

    public void discoverAppCapabilities() {
        discoverAppCapabilities(Thread.currentThread().getContextClassLoader());
    }

    public void discoverAppCapabilities(ClassLoader classLoader) {
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/casehub-mcp-app.properties");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream is = url.openStream()) {
                    Properties props = new Properties();
                    props.load(is);
                    String app = props.getProperty("app", "").trim();
                    String capability = props.getProperty("capability", "").trim();
                    String tagsStr = props.getProperty("tags", "").trim();
                    if (!app.isEmpty() && !capability.isEmpty()) {
                        List<String> tags = tagsStr.isEmpty() ? List.of()
                                : Arrays.stream(tagsStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                        registerAppCapability(app, new DomainContentFormatter.AppCapability(capability, tags));
                    }
                }
            }
        } catch (IOException ignored) {}
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
        if (query == null || query.isBlank()) return List.of();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        for (DomainModel domain : domains.values()) {
            boolean domainMatch = matchesDomain(domain, lowerQuery);
            for (OperationDescriptor op : domain.operations()) {
                if (domainMatch || matchesOperation(op, lowerQuery)) {
                    results.add(new SearchResult(domain.name(), op));
                }
            }
        }
        return List.copyOf(results);
    }

    private boolean matchesDomain(DomainModel domain, String lowerQuery) {
        if (domain.name().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (domain.app() != null && domain.app().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (!domain.summary().isEmpty() && domain.summary().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        DomainContentFormatter.AppCapability cap = appCapabilities.get(domain.app());
        if (cap != null) {
            if (cap.heading().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
            for (String tag : cap.tags()) {
                if (tag.toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
            }
        }
        return false;
    }

    private static boolean matchesOperation(OperationDescriptor op, String lowerQuery) {
        if (op.name().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (op.summary() != null && op.summary().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (op.returnTypeName() != null && op.returnTypeName().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        for (ParameterDescriptor param : op.params()) {
            if (param.name().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        }
        return false;
    }
}
