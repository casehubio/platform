package io.casehub.platform.mcp;

import java.util.List;

/**
 * Test stand-in for connectors-api's UnsupportedCapabilityException.
 * Mimics the same accessor pattern (capability(), provider(), supportedCapabilities())
 * without any shared type hierarchy — exactly the situation the dispatcher must handle.
 */
class ForeignCapabilityException extends RuntimeException {

    private final String capability;
    private final String provider;
    private final List<String> supportedCapabilities;

    ForeignCapabilityException(String capability, String provider,
                               List<String> supported, String message) {
        super(message);
        this.capability = capability;
        this.provider = provider;
        this.supportedCapabilities = List.copyOf(supported);
    }

    public String capability() { return capability; }
    public String provider() { return provider; }
    public List<String> supportedCapabilities() { return supportedCapabilities; }
}
