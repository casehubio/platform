package io.casehub.platform.api.callback;

import java.util.Map;

public record CallbackRegistrationRequest(
        String spiName,
        String callbackUrl,
        String credentialRef,
        String tenancyId,
        int timeoutMs,
        int ttlSeconds,
        Map<String, String> metadata) {

    public CallbackRegistrationRequest {
        if (spiName == null || spiName.isBlank())
            throw new IllegalArgumentException("spiName required");
        if (callbackUrl == null || callbackUrl.isBlank())
            throw new IllegalArgumentException("callbackUrl required");
        if (tenancyId == null || tenancyId.isBlank())
            throw new IllegalArgumentException("tenancyId required");
        if (ttlSeconds <= 0) ttlSeconds = 300;
        if (timeoutMs <= 0) timeoutMs = 30000;
        if (metadata == null) metadata = Map.of();
    }
}
