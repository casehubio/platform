package io.casehub.platform.api.delivery;

public record DeliveryResult(
        boolean success,
        String failureReason,
        String sourceId
) {
    public DeliveryResult(boolean success, String failureReason) {
        this(success, failureReason, null);
    }
}
