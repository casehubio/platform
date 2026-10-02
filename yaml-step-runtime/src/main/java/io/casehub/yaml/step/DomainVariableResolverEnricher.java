package io.casehub.yaml.step;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.DomainVariableSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class DomainVariableResolverEnricher {

    private final List<DomainVariableSource> sources;

    @Inject
    public DomainVariableResolverEnricher(Instance<DomainVariableSource> sources) {
        this.sources = sources.stream().toList();
    }

    DomainVariableResolverEnricher(List<DomainVariableSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public VariableResolver enrich(VariableResolver base) {
        var resolver = base;
        for (var source : sources) {
            resolver = resolver.withObjectScope(source.prefix(), source::resolve);
        }
        return resolver;
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }
}
