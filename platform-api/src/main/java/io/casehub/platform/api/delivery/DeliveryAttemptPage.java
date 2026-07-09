package io.casehub.platform.api.delivery;

import java.util.List;

public record DeliveryAttemptPage(
        List<DeliveryAttempt> attempts,
        String nextCursor
) {
    public DeliveryAttemptPage {
        attempts = List.copyOf(attempts);
    }
}
