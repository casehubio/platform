package io.casehub.platform.api.delivery;

import java.util.Objects;

public record DeliveryExhausted(DeliveryAttempt attempt) {
    public DeliveryExhausted {
        Objects.requireNonNull(attempt, "attempt");
    }
}
