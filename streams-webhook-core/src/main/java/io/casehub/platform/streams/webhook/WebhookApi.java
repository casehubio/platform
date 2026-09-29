package io.casehub.platform.streams.webhook;

public interface WebhookApi {

    WebhookResult receive(byte[] body, String tenancyId, String streamId, String authorization);
}
