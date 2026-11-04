package io.casehub.yaml.plugin.api;

public final class FailureCategory {
    public static final String TIMEOUT = "timeout";
    public static final String TRANSIENT = "transient";
    public static final String PERMANENT = "permanent";
    public static final String RATE_LIMITED = "rate-limit";

    private FailureCategory() {}
}
