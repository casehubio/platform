package io.casehub.yaml.step;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.DomainVariableSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainVariableResolverEnricherTest {

    @Test
    void enrich_adds_domain_source_as_object_scope() {
        DomainVariableSource source = new DomainVariableSource() {
            @Override public String prefix() { return "test"; }
            @Override public Object resolve(String path) {
                return "hello".equals(path) ? "world" : null;
            }
        };

        var enricher = new DomainVariableResolverEnricher(List.of(source));
        var base = new VariableResolver(Map.of(), Set.of());
        var enriched = enricher.enrich(base);

        assertThat(enriched.resolve("${test.hello}")).isEqualTo("world");
    }

    @Test
    void enrich_with_no_sources_returns_base() {
        var enricher = new DomainVariableResolverEnricher(List.of());
        var base = new VariableResolver(Map.of(), Set.of());
        var enriched = enricher.enrich(base);

        assertThat(enriched).isNotNull();
        assertThat(enricher.hasSources()).isFalse();
    }

    @Test
    void enrich_with_multiple_sources() {
        DomainVariableSource device = new DomainVariableSource() {
            @Override public String prefix() { return "device"; }
            @Override public Object resolve(String path) {
                return "lock-1".equals(path) ? Map.of("isLocked", true) : null;
            }
        };
        DomainVariableSource service = new DomainVariableSource() {
            @Override public String prefix() { return "service"; }
            @Override public Object resolve(String path) {
                return "api".equals(path) ? "healthy" : null;
            }
        };

        var enricher = new DomainVariableResolverEnricher(List.of(device, service));
        var enriched = enricher.enrich(new VariableResolver(Map.of(), Set.of()));

        assertThat(enricher.hasSources()).isTrue();
    }
}
