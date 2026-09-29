package io.casehub.yaml.plugin.api;

public enum RuntimeType {
    JAVA, TS;

    public boolean canExecute(Portability portability) {
        return switch (portability) {
            case UNIVERSAL, BOTH -> true;
            case JAVA -> this == JAVA;
            case TS -> this == TS;
        };
    }
}
