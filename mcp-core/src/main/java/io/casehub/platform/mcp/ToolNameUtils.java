package io.casehub.platform.mcp;

public final class ToolNameUtils {

    private ToolNameUtils() {}

    public static String normalise(String domain) {
        return domain.replace("/", "_");
    }

    public static String toolName(String domain, String operation) {
        return normalise(domain) + "_" + operation;
    }

    public static String reportToolName(String domain) {
        return normalise(domain) + "_report";
    }
}
