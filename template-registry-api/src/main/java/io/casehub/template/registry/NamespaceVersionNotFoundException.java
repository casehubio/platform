package io.casehub.template.registry;

public class NamespaceVersionNotFoundException extends RuntimeException {

    private final String namespace;
    private final long requestedVersion;
    private final long currentVersion;

    public NamespaceVersionNotFoundException(String namespace,
                                              long requestedVersion,
                                              long currentVersion) {
        super("Namespace '" + namespace + "' version " + requestedVersion
              + " not found (current: " + currentVersion
              + "). The requested version may have been evicted from the history ring.");
        this.namespace = namespace;
        this.requestedVersion = requestedVersion;
        this.currentVersion = currentVersion;
    }

    public String namespace() { return namespace; }
    public long requestedVersion() { return requestedVersion; }
    public long currentVersion() { return currentVersion; }
}
