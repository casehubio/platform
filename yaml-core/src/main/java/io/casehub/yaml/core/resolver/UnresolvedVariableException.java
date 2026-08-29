package io.casehub.yaml.core.resolver;

public class UnresolvedVariableException extends RuntimeException {

    private final String variableName;
    private final String elementContext;

    public UnresolvedVariableException(String variableName, String elementContext, String detail) {
        super("Unresolved variable '" + variableName + "' in element '" + elementContext + "'. " + detail);
        this.variableName = variableName;
        this.elementContext = elementContext;
    }

    public String variableName() { return variableName; }

    public String elementContext() { return elementContext; }
}
