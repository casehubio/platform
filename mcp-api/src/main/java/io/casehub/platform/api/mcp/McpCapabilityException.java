package io.casehub.platform.api.mcp;

import java.util.List;

public class McpCapabilityException extends RuntimeException {

    private final OperationOutcome outcome;
    private final String capability;
    private final String provider;
    private final List<String> supportedCapabilities;

    private McpCapabilityException(OperationOutcome outcome, String capability,
                                    String provider, List<String> supportedCapabilities,
                                    String message) {
        super(message);
        this.outcome = outcome;
        this.capability = capability;
        this.provider = provider;
        this.supportedCapabilities = List.copyOf(supportedCapabilities);
    }

    public static McpCapabilityException unsupported(String capability, String provider,
                                                      List<String> supported, String message) {
        return new McpCapabilityException(OperationOutcome.UNSUPPORTED,
                capability, provider, supported, message);
    }

    public static McpCapabilityException unavailable(String capability, String provider,
                                                      String message) {
        return new McpCapabilityException(OperationOutcome.UNAVAILABLE,
                capability, provider, List.of(), message);
    }

    public OperationOutcome outcome() { return outcome; }
    public String capability() { return capability; }
    public String provider() { return provider; }
    public List<String> supportedCapabilities() { return supportedCapabilities; }
}
