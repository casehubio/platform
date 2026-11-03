package io.casehub.platform.compiler;

import io.casehub.yaml.core.module.ParameterValidator;
import io.casehub.yaml.core.module.YamlModuleParameter;
import io.casehub.yaml.core.playbook.PlaybookVariableParser;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.type.TypedMap;

import java.util.Map;
import java.util.Set;

public record PlaybookVariableContext(
        Map<String, YamlModuleParameter> params,
        TypedMap vars) {

    public static final PlaybookVariableContext EMPTY =
        new PlaybookVariableContext(Map.of(), new TypedMap(Map.of(), Map.of()));

    public static PlaybookVariableContext from(PlaybookVariableParser.ParsedVariables parsed) {
        return new PlaybookVariableContext(parsed.params(), parsed.vars());
    }

    public VariableResolver buildResolver(Map<String, String> callerParams,
                                          Set<String> deferredPrefixes) {
        if (!params.isEmpty()) {
            ParameterValidator.validateOrThrow(params, callerParams);
        }
        VariableResolver resolver = VariableResolver.forParams(params, callerParams, deferredPrefixes);
        if (!vars.values().isEmpty()) {
            resolver = resolver.withObjectScope("var",
                name -> vars.values().get(name));
        }
        return resolver;
    }
}
