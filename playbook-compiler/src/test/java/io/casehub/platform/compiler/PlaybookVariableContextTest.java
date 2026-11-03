package io.casehub.platform.compiler;

import io.casehub.yaml.core.module.ParameterValidationException;
import io.casehub.yaml.core.module.YamlModuleParameter;
import io.casehub.yaml.core.type.TypedMap;
import io.casehub.yaml.core.type.ValueType;
import io.casehub.yaml.plugin.api.ParameterType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookVariableContextTest {

    @Test
    void buildResolver_resolves_var_prefix() {
        var vars = new TypedMap(
            Map.of("count", ValueType.INTEGER),
            Map.of("count", (Object) 42));
        var ctx = new PlaybookVariableContext(Map.of(), vars);

        var resolver = ctx.buildResolver(Map.of(), Set.of());

        assertThat(resolver.resolve("${var.count}")).isEqualTo(42);
    }

    @Test
    void buildResolver_resolves_params_prefix() {
        var params = Map.of("portfolio",
            YamlModuleParameter.builder().type(ParameterType.STRING).required(true).build());
        var ctx = new PlaybookVariableContext(params, new TypedMap(Map.of(), Map.of()));

        var resolver = ctx.buildResolver(Map.of("portfolio", "growth-fund"), Set.of());

        assertThat(resolver.resolve("${params.portfolio}")).isEqualTo("growth-fund");
    }

    @Test
    void buildResolver_validates_missing_required_param() {
        var params = Map.of("portfolio",
            YamlModuleParameter.builder().type(ParameterType.STRING).required(true).build());
        var ctx = new PlaybookVariableContext(params, new TypedMap(Map.of(), Map.of()));

        assertThatThrownBy(() -> ctx.buildResolver(Map.of(), Set.of()))
            .isInstanceOf(ParameterValidationException.class);
    }

    @Test
    void empty_context_builds_resolver_without_error() {
        var resolver = PlaybookVariableContext.EMPTY.buildResolver(Map.of(), Set.of());
        assertThat(resolver).isNotNull();
    }
}
