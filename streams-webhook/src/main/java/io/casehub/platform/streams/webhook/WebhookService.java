package io.casehub.platform.streams.webhook;

import io.casehub.platform.api.mcp.HeaderParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@McpDomain(value = "casehub/streams-webhook", app = "platform",
        summary = "CloudEvents webhook receiver — accepts structured CloudEvents via HTTP POST",
        basePath = "/streams/webhook")
public class WebhookService implements WebhookApi {

    private final WebhookReceiver receiver;

    @Inject
    public WebhookService(WebhookReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    @PlatformWebhook(value = "Receive a structured CloudEvent via HTTP POST", consumes = "application/cloudevents+json")
    @RestPath("/{tenancyId}/{streamId}")
    public WebhookResult receive(byte[] body,
                                 @PathParam("tenancyId") String tenancyId,
                                 @PathParam("streamId") String streamId,
                                 @HeaderParam("Authorization") String authorization) {
        Map<String, String> headers = authorization != null
                ? Map.of("Authorization", authorization)
                : Map.of();
        return receiver.receive(body, tenancyId, streamId, headers);
    }
}
