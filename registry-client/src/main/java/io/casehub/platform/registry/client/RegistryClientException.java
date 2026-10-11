package io.casehub.platform.registry.client;

public class RegistryClientException extends RuntimeException {

    private final int statusCode;

    public RegistryClientException(final String message, final int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public RegistryClientException(final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }
}
