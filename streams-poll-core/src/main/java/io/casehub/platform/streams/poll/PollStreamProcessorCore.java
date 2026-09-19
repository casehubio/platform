package io.casehub.platform.streams.poll;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.streams.StreamCloudEventFactory;
import io.cloudevents.CloudEvent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PollStreamProcessorCore {

    private static final Logger LOG = Logger.getLogger(PollStreamProcessorCore.class.getName());
    private static final String FALLBACK_TYPE = "io.casehub.platform.streams.poll.unregistered";

    private final EndpointRegistry endpointRegistry;
    private final Consumer<CloudEvent> eventCallback;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PollStreamProcessorCore(EndpointRegistry endpointRegistry,
                                   Consumer<CloudEvent> eventCallback) {
        this.endpointRegistry = endpointRegistry;
        this.eventCallback = eventCallback;
    }

    public void poll() {
        endpointRegistry.discover(
            new EndpointQuery(TenancyConstants.DEFAULT_TENANT_ID, null,
                EndpointProtocol.HTTP, Set.of(EndpointCapability.QUERY))
        ).forEach(descriptor -> {
            try {
                pollAndFire(descriptor);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Poll failed for endpoint "
                    + descriptor.properties().get(EndpointPropertyKeys.URL), e);
            }
        });
    }

    byte[] fetchBytes(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Poll interrupted for " + url, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Poll returned HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    void pollAndFire(EndpointDescriptor descriptor) throws IOException {
        String url = descriptor.properties().get(EndpointPropertyKeys.URL);
        byte[] body = fetchBytes(url);
        String urlEncoded = URLEncoder.encode(url, StandardCharsets.UTF_8);
        URI source = URI.create("/platform/streams/poll/" + urlEncoded);
        CloudEvent ce = StreamCloudEventFactory.build(body, descriptor, null, source, FALLBACK_TYPE);
        eventCallback.accept(ce);
    }
}
