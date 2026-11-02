package io.casehub.plugin.catalog;

public class ConflictException extends RuntimeException {

    private final String capability;
    private final PluginCoordinates existing;
    private final PluginCoordinates incoming;

    public ConflictException(String capability,
                             PluginCoordinates existing,
                             PluginCoordinates incoming) {
        super("Capability '" + capability + "' already provided by "
                + existing + ", cannot install " + incoming);
        this.capability = capability;
        this.existing = existing;
        this.incoming = incoming;
    }

    public String capability() { return capability; }
    public PluginCoordinates existing() { return existing; }
    public PluginCoordinates incoming() { return incoming; }
}
