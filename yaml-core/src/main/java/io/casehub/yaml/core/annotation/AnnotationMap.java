package io.casehub.yaml.core.annotation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AnnotationMap(
        List<Annotation> documentAnnotations,
        Map<String, List<Annotation>> stepAnnotations) {

    private static final AnnotationMap EMPTY = new AnnotationMap(List.of(), Map.of());

    public AnnotationMap {
        Objects.requireNonNull(documentAnnotations, "documentAnnotations");
        Objects.requireNonNull(stepAnnotations, "stepAnnotations");
        documentAnnotations = List.copyOf(documentAnnotations);
        stepAnnotations = Map.copyOf(stepAnnotations);
    }

    public static AnnotationMap empty() { return EMPTY; }

    public List<Annotation> forStep(int index) {
        return stepAnnotations.getOrDefault(String.valueOf(index), List.of());
    }

    public boolean hasStepAnnotation(int index, String name) {
        return forStep(index).stream().anyMatch(a -> a.name().equals(name));
    }

    public Optional<Annotation> forDocument(String name) {
        return documentAnnotations.stream().filter(a -> a.name().equals(name)).findFirst();
    }
}
