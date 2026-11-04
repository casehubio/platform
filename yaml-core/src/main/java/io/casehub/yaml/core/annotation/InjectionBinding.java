package io.casehub.yaml.core.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InjectionBinding(
        String variableName,
        String typeHint,
        String typeName,
        Map<String, String> qualifiers) {

    public InjectionBinding {
        qualifiers = qualifiers != null ? Map.copyOf(qualifiers) : Map.of();
    }

    public static List<InjectionBinding> extractAll(AnnotationMap annotationMap, Map<String, Object> contentMap) {
        List<Annotation> injects = annotationMap.documentAnnotations().stream()
            .filter(a -> "Inject".equals(a.name()))
            .toList();

        if (injects.isEmpty()) return List.of();

        List<String> bindingKeys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : contentMap.entrySet()) {
            String key = entry.getKey();
            if (!"name".equals(key) && !"steps".equals(key) && !"params".equals(key)
                    && !"vars".equals(key) && !"states".equals(key) && !"scenario".equals(key)
                    && !"simulate".equals(key) && entry.getValue() instanceof String) {
                bindingKeys.add(key);
            }
        }

        List<InjectionBinding> result = new ArrayList<>();
        int limit = Math.min(injects.size(), bindingKeys.size());
        for (int i = 0; i < limit; i++) {
            Annotation inject = injects.get(i);
            String bindingKey = bindingKeys.get(i);
            String typeName = inject.stringArg(0);
            Map<String, String> qualifiers = new LinkedHashMap<>();
            for (AnnotationArg arg : inject.args()) {
                if (arg instanceof AnnotationArg.Named n) {
                    qualifiers.put(n.name(), n.value());
                }
            }
            result.add(new InjectionBinding(
                bindingKey,
                String.valueOf(contentMap.get(bindingKey)),
                typeName, qualifiers));
        }

        return result;
    }
}
