package io.casehub.yaml.plugin.api;

public final class FailureCategory {
    public static final String TIMEOUT = "TIMEOUT";
    public static final String TRANSIENT = "TRANSIENT";
    public static final String PERMANENT = "PERMANENT";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private FailureCategory() {}
}
