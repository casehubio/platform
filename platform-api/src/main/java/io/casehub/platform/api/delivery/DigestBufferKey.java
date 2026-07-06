package io.casehub.platform.api.delivery;

import java.util.Objects;

public record DigestBufferKey(String userId, String tenancyId, String channelId) {
    public DigestBufferKey {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(channelId, "channelId");
    }
}
