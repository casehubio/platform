package io.casehub.platform.rest.spring.generator;

public record ProviderDescriptor(
        String className,
        ProviderKind providerKind,
        String targetType,
        int priority
) {
    private static final int AUTHENTICATION_PRIORITY = 1000;

    public boolean isSecurityFilter() {
        return providerKind == ProviderKind.REQUEST_FILTER && priority > 0 && priority <= AUTHENTICATION_PRIORITY;
    }

    public enum ProviderKind {
        EXCEPTION_MAPPER,
        REQUEST_FILTER,
        RESPONSE_FILTER,
        PARAM_CONVERTER
    }
}
