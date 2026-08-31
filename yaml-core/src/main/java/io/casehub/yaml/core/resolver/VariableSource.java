package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface VariableSource {

    String resolve(String name);

    static VariableSource chain(VariableSource... sources) {
        return name -> {
            for (VariableSource source : sources) {
                String value = source.resolve(name);
                if (value != null) return value;
            }
            return null;
        };
    }

    static VariableSource env() {
        return System::getenv;
    }

    static VariableSource systemProperty() {
        return System::getProperty;
    }
}
