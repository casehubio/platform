package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

public class CorrelationTimeoutException extends TimeoutException {
    private final Object correlationKey;
    private final Duration timeout;

    public CorrelationTimeoutException(Object correlationKey, Duration timeout) {
        super("Correlation timeout for key '" + correlationKey + "' after " + timeout);
        this.correlationKey = correlationKey;
        this.timeout = timeout;
    }

    public Object correlationKey() { return correlationKey; }
    public Duration timeout() { return timeout; }
}
