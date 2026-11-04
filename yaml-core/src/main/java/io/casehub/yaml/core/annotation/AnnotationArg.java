package io.casehub.yaml.core.annotation;

public sealed interface AnnotationArg {
    record Positional(String value) implements AnnotationArg {}
    record Named(String name, String value) implements AnnotationArg {}
}
