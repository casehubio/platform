package io.casehub.platform.api.delivery;

import java.util.List;

public interface EngagementCallbackHandler {

    String channelId();

    /**
     * Translates a provider-specific webhook payload into platform engagement events.
     * Implementations MUST verify payload authenticity (e.g. HMAC signature) and throw
     * on invalid signatures to prevent forged engagement events.
     */
    List<RawEngagement> translate(String rawPayload);
}
