package io.casehub.platform.streams.webhook;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Startup
@ApplicationScoped
@jakarta.ws.rs.Path("/streams/webhook")
public class WebhookResource {

    private static final Logger LOG = Logger.getLogger(WebhookResource.class);

    @Inject
    Event<CloudEvent> cloudEventBus;

    @Inject
    EndpointRegistry endpointRegistry;

    @Inject
    CredentialResolver credentialResolver;

    @ConfigProperty(name = "casehub.streams.webhook.public-url")
    String publicUrl;

    @ConfigProperty(name = "casehub.streams.webhook.require-auth", defaultValue = "true")
    boolean requireAuth;

    @Context
    HttpHeaders httpHeaders;

    private EventFormat eventFormat;

    @PostConstruct
    void init() {
        eventFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
        if (eventFormat == null) {
            throw new IllegalStateException(
                    "CloudEvents JSON format not registered — cloudevents-json-jackson missing from classpath");
        }

        endpointRegistry.register(new EndpointDescriptor(
                Path.of("platform", "streams", "webhook"),
                TenancyConstants.PLATFORM_TENANT_ID,
                EndpointType.SERVICE,
                EndpointProtocol.HTTP,
                Map.of(EndpointPropertyKeys.URL, publicUrl),
                null,
                Set.of(EndpointCapability.RECEIVE)));
    }

    @POST
    @jakarta.ws.rs.Path("/{tenancyId}/{streamId}")
    @Consumes("application/cloudevents+json")
    public Response receive(
            byte[] body,
            @PathParam("tenancyId") String tenancyIdFromPath,
            @PathParam("streamId") String streamId) {

        CloudEvent incoming;
        try {
            incoming = eventFormat.deserialize(body);
        } catch (RuntimeException e) {
            return Response.status(400)
                           .entity("Invalid CloudEvent body: " + e.getMessage())
                           .build();
        }

        Optional<EndpointDescriptor> descriptor =
                endpointRegistry.resolve(Path.of("streams", streamId), tenancyIdFromPath);
        if (descriptor.isEmpty()) {
            return Response.status(404).build();
        }

        Response authFailure = validateCredentials(descriptor.get(), streamId);
        if (authFailure != null) {
            return authFailure;
        }

        CloudEvent enriched = CloudEventBuilder.from(incoming)
                                               .withExtension("tenancyid", descriptor.get().tenancyId())
                                               .build();

        cloudEventBus.fireAsync(enriched)
                     .whenComplete((e, t) -> {
                         if (t != null) {LOG.warnf(t, "CloudEvent observer failed for stream %s", streamId);}
                     });

        return Response.accepted().build();
    }

    private Response validateCredentials(EndpointDescriptor descriptor, String streamId) {
        if (descriptor.credentialRef() != null) {
            Map<String, String> creds         = credentialResolver.resolve(descriptor.credentialRef());
            String              expectedToken = creds.get(CredentialPropertyKeys.BEARER_TOKEN);
            if (expectedToken != null) {
                String authHeader = httpHeaders.getHeaderString("Authorization");
                if (authHeader == null || !authHeader.equals("Bearer " + expectedToken)) {
                    return Response.status(401).build();
                }
            }
        } else if (requireAuth) {
            LOG.warnf("Webhook endpoint streams/%s has no credentialRef — rejected (require-auth=true). " +
                      "Set credentialRef on the EndpointDescriptor or set casehub.streams.webhook.require-auth=false",
                      streamId);
            return Response.status(401).build();
        }
        return null;
    }
}
