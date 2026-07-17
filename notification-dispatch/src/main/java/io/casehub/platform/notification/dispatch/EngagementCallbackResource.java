package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.EngagementCallbackHandler;
import io.casehub.platform.api.delivery.EngagementType;
import io.casehub.platform.api.identity.CurrentPrincipal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.stream.Collectors;

@Path("/delivery/engagement")
@ApplicationScoped
public class EngagementCallbackResource {

    private static final Logger LOG = Logger.getLogger(EngagementCallbackResource.class);

    private final DeliveryAttemptStore store;
    private final EngagementRecorder recorder;
    private final CurrentPrincipal principal;
    private final Map<String, EngagementCallbackHandler> handlers;
    private final boolean enabled;

    @Inject
    public EngagementCallbackResource(DeliveryAttemptStore store,
                                      EngagementRecorder recorder,
                                      CurrentPrincipal principal,
                                      Instance<EngagementCallbackHandler> handlerInstances,
                                      @ConfigProperty(name = "casehub.delivery.engagement.enabled", defaultValue = "false")
                                      boolean enabled) {
        this.store = store;
        this.recorder = recorder;
        this.principal = principal;
        this.handlers = handlerInstances.stream()
                .collect(Collectors.toMap(EngagementCallbackHandler::channelId, h -> h));
        this.enabled = enabled;
    }

    EngagementCallbackResource(DeliveryAttemptStore store,
                               EngagementRecorder recorder,
                               CurrentPrincipal principal,
                               Map<String, EngagementCallbackHandler> handlers,
                               boolean enabled) {
        this.store = store;
        this.recorder = recorder;
        this.principal = principal;
        this.handlers = handlers;
        this.enabled = enabled;
    }

    @POST
    @Path("/callback/{channelId}")
    @Consumes({"application/json", "application/x-www-form-urlencoded"})
    public Response handleCallback(@PathParam("channelId") String channelId, String rawPayload) {
        if (!enabled) {
            return Response.status(404).build();
        }
        var handler = handlers.get(channelId);
        if (handler == null) {
            return Response.status(404).build();
        }
        try {
            var rawEvents = handler.translate(rawPayload);
            if (rawEvents != null) {
                for (var raw : rawEvents) {
                    DeliveryAttempt attempt = store.findById(raw.attemptId());
                    if (attempt == null) {
                        LOG.debugf("Engagement callback for nonexistent attempt '%s' — skipping", raw.attemptId());
                        continue;
                    }
                    recorder.record(attempt, raw.type(), raw.metadata());
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Engagement callback handler '%s' failed to translate payload", channelId);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/{attemptId}")
    @Consumes("application/json")
    public Response recordDirect(@PathParam("attemptId") String attemptId,
                                 DirectEngagementRequest request) {
        if (!enabled) {
            return Response.status(404).build();
        }
        if (request.type() == null) {
            return Response.status(400).build();
        }
        DeliveryAttempt attempt = store.findById(attemptId);
        if (attempt == null) {
            return Response.status(404).build();
        }
        if (!attempt.tenancyId().equals(principal.tenancyId())) {
            return Response.status(403).build();
        }
        recorder.record(attempt, request.type(), request.metadata());
        return Response.ok().build();
    }

    public record DirectEngagementRequest(EngagementType type, String metadata) {}
}
