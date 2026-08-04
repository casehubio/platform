package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.EngagementCallbackHandler;
import io.casehub.platform.api.delivery.EngagementType;
import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.stream.Collectors;

@Path("/delivery/engagement")
@ApplicationScoped
public class EngagementCallbackResource {

    private static final Logger LOG = Logger.getLogger(EngagementCallbackResource.class);

    private final DeliveryAttemptStore                   store;
    private final EngagementRecorder                     recorder;
    private final CurrentPrincipal                       principal;
    private final Map<String, EngagementCallbackHandler> handlers;
    private final PreferenceProvider                     preferenceProvider;

    @Context
    HttpHeaders httpHeaders;

    @Inject
    public EngagementCallbackResource(DeliveryAttemptStore store,
                                      EngagementRecorder recorder,
                                      CurrentPrincipal principal,
                                      Instance<EngagementCallbackHandler> handlerInstances,
                                      PreferenceProvider preferenceProvider) {
        this.store              = store;
        this.recorder           = recorder;
        this.principal          = principal;
        this.handlers           = handlerInstances.stream()
                                                   .collect(Collectors.toMap(EngagementCallbackHandler::channelId, h -> h));
        this.preferenceProvider = preferenceProvider;
    }

    EngagementCallbackResource(DeliveryAttemptStore store,
                               EngagementRecorder recorder,
                               CurrentPrincipal principal,
                               Map<String, EngagementCallbackHandler> handlers,
                               PreferenceProvider preferenceProvider,
                               HttpHeaders httpHeaders) {
        this.store              = store;
        this.recorder           = recorder;
        this.principal          = principal;
        this.handlers           = handlers;
        this.preferenceProvider = preferenceProvider;
        this.httpHeaders        = httpHeaders;
    }

    @POST
    @Path("/callback/{channelId}")
    @Consumes({"application/json", "application/x-www-form-urlencoded"})
    public Response handleCallback(@PathParam("channelId") String channelId, String rawPayload) {
        if (!isEngagementEnabled()) {
            return Response.status(404).build();
        }
        var handler = handlers.get(channelId);
        if (handler == null) {
            return Response.status(404).build();
        }
        try {
            var rawEvents = handler.translate(rawPayload, extractHeaders());
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
        } catch (SecurityException e) {
            LOG.warnf("Engagement callback handler '%s' rejected payload: %s", channelId, e.getMessage());
            return Response.status(401).build();
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
        if (!isEngagementEnabled()) {
            return Response.status(404).build();
        }
        if (request.type() == null) {
            return Response.status(400).build();
        }
        DeliveryAttempt attempt = store.findById(attemptId, principal.tenancyId());
        if (attempt == null) {
            return Response.status(404).build();
        }
        recorder.record(attempt, request.type(), request.metadata());
        return Response.ok().build();
    }

    private boolean isEngagementEnabled() {
        return preferenceProvider
                .resolve(SettingsScope.root(TenancyConstants.PLATFORM_TENANT_ID))
                .getOrDefault(PlatformPreferenceKeys.ENGAGEMENT_ENABLED)
                .value();
    }

    private Map<String, String> extractHeaders() {
        if (httpHeaders == null) {return Map.of();}
        Map<String, String> result = new java.util.HashMap<>();
        for (var key : httpHeaders.getRequestHeaders().keySet()) {
            result.put(key, httpHeaders.getHeaderString(key));
        }
        return result;
    }

    public record DirectEngagementRequest(EngagementType type, String metadata) {}
}
