package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface ObjectVariableSource {
    Object resolve(String name);

    default boolean allowContainerReturn() { return true; }

    static ObjectVariableSource drillOnly(ObjectVariableSource source) {
        return new ObjectVariableSource() {
            @Override public Object resolve(String name) { return source.resolve(name); }
            @Override public boolean allowContainerReturn() { return false; }
        };
    }
}
