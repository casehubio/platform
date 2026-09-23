package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@McpDomain(value = "delivery/engagement", basePath = "/delivery/engagement")
@ApplicationScoped
public class EngagementCallbackResource {

    private static final Logger LOG = Logger.getLogger(EngagementCallbackResource.class);

    private final EngagementCallbackService service;

    @Inject
    public EngagementCallbackResource(EngagementCallbackService service) {
        this.service = service;
    }

    @PlatformWebhook(value = "Delivery channel callback", consumes = {"application/json", "application/x-www-form-urlencoded"})
    @RestPath("/callback/{channelId}")
    public Response handleCallback(@PathParam String channelId, String rawPayload,
                                   @ContextParam("httpHeaders") Map<String, List<String>> headers) {
        Map<String, String> flatHeaders = headers != null
                                          ? headers.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() != null && !e.getValue().isEmpty() ? e.getValue().getFirst() : ""))
                                          : Map.of();
        try {
            service.handleCallback(channelId, rawPayload, flatHeaders);
            return Response.ok().build();
        } catch (IllegalStateException e) {
            return Response.status(404).build();
        } catch (IllegalArgumentException e) {
            return Response.status(404).build();
        } catch (SecurityException e) {
            LOG.warnf("Engagement callback handler '%s' rejected payload: %s", channelId, e.getMessage());
            return Response.status(401).build();
        } catch (Exception e) {
            LOG.warnf(e, "Engagement callback handler '%s' failed", channelId);
            return Response.ok().build();
        }
    }

    @PlatformMutation("Record direct engagement")
    @RestPath("/{attemptId}")
    public Response recordDirect(@PathParam String attemptId,
                                 DirectEngagementRequest request) {
        try {
            service.recordDirect(attemptId, request);
            return Response.ok().build();
        } catch (IllegalStateException e) {
            return Response.status(404).build();
        } catch (IllegalArgumentException e) {
            return Response.status(e.getMessage().contains("not found") ? 404 : 400).build();
        }
    }
}
