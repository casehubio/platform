package io.casehub.yaml.core.annotation;

import java.util.List;
import java.util.Objects;

public record Annotation(String name, List<AnnotationArg> args) {

    public Annotation {
        Objects.requireNonNull(name, "name");
        args = args != null ? List.copyOf(args) : List.of();
    }

    public boolean hasArg(String argName) {
        return args.stream().anyMatch(a -> a instanceof AnnotationArg.Named n && n.name().equals(argName));
    }

    public String stringArg(int index) {
        if (index < 0 || index >= args.size()) return null;
        return switch (args.get(index)) {
            case AnnotationArg.Positional p -> p.value();
            case AnnotationArg.Named n -> n.value();
        };
    }

    public String namedArg(String argName) {
        return args.stream()
            .filter(a -> a instanceof AnnotationArg.Named n && n.name().equals(argName))
            .map(a -> ((AnnotationArg.Named) a).value())
            .findFirst().orElse(null);
    }
}
