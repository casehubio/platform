package io.casehub.platform.api.delivery;

import java.util.List;
import java.util.Map;

public interface EngagementCallbackHandler {

    String channelId();

    /**
     * Translates a provider-specific webhook payload into platform engagement events.
     *
     * <p>Implementations MUST verify the request signature using provider-specific
     * headers (e.g. {@code X-Hub-Signature-256}) before processing the payload.
     * Implementations MUST throw {@link SecurityException} on verification failure.
     *
     * @param rawPayload the raw webhook body
     * @param headers    HTTP request headers; implementations extract the relevant verification header
     * @return translated engagement events
     * @throws SecurityException if signature verification fails
     */
    List<RawEngagement> translate(String rawPayload, Map<String, String> headers);
}
