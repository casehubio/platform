package io.casehub.platform.compiler;

import io.casehub.template.registry.TemplateSchema;
import java.util.List;

@FunctionalInterface
public interface TemplateSchemaResolver {
    List<TemplateSchema> resolve(String namespace);

    static TemplateSchemaResolver noOp() {
        return namespace -> List.of();
    }
}
