package io.casehub.platform.template;

import io.casehub.template.registry.MatchTrace;
import io.casehub.template.registry.MatchTraceResult;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateSchema;
import io.casehub.template.registry.UnifiedTemplateRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class NoOpUnifiedTemplateRegistry implements UnifiedTemplateRegistry {

    @Override
    public void register(TemplateNamespace namespace) {}

    @Override
    public void deregister(String namespaceName) {}

    @Override
    public TemplateMatch match(String input) {
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    public TemplateMatch match(String namespaceName, String input) {
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    public TemplateMatch match(String namespaceName, String input, long version) {
        return new TemplateMatch.Unmatched(input);
    }


    @Override
    public MatchTraceResult matchWithTrace(String input) {
        return new MatchTraceResult(new TemplateMatch.Unmatched(input),
                                    new MatchTrace(List.of(), null, 0.0));
    }

    @Override
    public MatchTraceResult matchWithTrace(String namespaceName, String input) {
        return new MatchTraceResult(new TemplateMatch.Unmatched(input),
                                    new MatchTrace(List.of(), null, 0.0));
    }


    @Override
    public String generate(String namespaceName, String templateName,
                            Map<String, String> values) {
        throw new UnsupportedOperationException(
            "Template generation requires a registry implementation on the classpath");
    }

    @Override
    public Optional<TemplateSchema> schema(String namespaceName,
                                            String templateName) {
        return Optional.empty();
    }

    @Override
    public List<TemplateSchema> schemas(String namespaceName) {
        return List.of();
    }

    @Override
    public Set<String> namespaces() {
        return Set.of();
    }
}
