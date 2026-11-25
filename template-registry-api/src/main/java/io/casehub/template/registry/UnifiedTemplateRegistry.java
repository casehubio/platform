package io.casehub.template.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface UnifiedTemplateRegistry {

    void register(TemplateNamespace namespace);

    void deregister(String namespaceName);

    TemplateMatch match(String input);

    TemplateMatch match(String namespaceName, String input);

    MatchTraceResult matchWithTrace(String input);

    MatchTraceResult matchWithTrace(String namespaceName, String input);


    String generate(String namespaceName, String templateName,
                    Map<String, String> values);

    Optional<TemplateSchema> schema(String namespaceName,
                                     String templateName);

    List<TemplateSchema> schemas(String namespaceName);

    Set<String> namespaces();
}
