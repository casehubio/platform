package io.casehub.platform.streams.camel;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.streams.StreamCloudEventFactory;
import io.cloudevents.CloudEvent;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CamelStreamProcessorCore {

    private static final Logger LOG = Logger.getLogger(CamelStreamProcessorCore.class.getName());
    private static final String FALLBACK_TYPE = "io.casehub.platform.streams.camel.unregistered";
    private static final URI SOURCE = URI.create("/platform/streams/camel");

    private final CamelContext camelContext;
    private final EndpointRegistry endpointRegistry;
    private final Consumer<CloudEvent> eventCallback;
    private final AtomicBoolean camelStarted = new AtomicBoolean(false);
    private final Set<String> routedUris = ConcurrentHashMap.newKeySet();

    public CamelStreamProcessorCore(CamelContext camelContext,
                                    EndpointRegistry endpointRegistry,
                                    Consumer<CloudEvent> eventCallback) {
        this.camelContext = camelContext;
        this.endpointRegistry = endpointRegistry;
        this.eventCallback = eventCallback;
    }

    public void init() {
        endpointRegistry.discover(
            new EndpointQuery(TenancyConstants.DEFAULT_TENANT_ID, null,
                EndpointProtocol.CAMEL, Set.of(EndpointCapability.RECEIVE))
        ).forEach(d -> {
            String uri = d.properties().get(EndpointPropertyKeys.URL);
            if (routedUris.add(uri)) {
                addRoute(d);
            }
        });
        camelStarted.set(true);
    }

    public void onEndpointRegistered(EndpointDescriptor descriptor) {
        if (descriptor.protocol() != EndpointProtocol.CAMEL) return;
        if (!camelStarted.get()) return;
        String uri = descriptor.properties().get(EndpointPropertyKeys.URL);
        if (routedUris.add(uri)) {
            addRoute(descriptor);
        }
    }

    private void addRoute(EndpointDescriptor d) {
        String uri = d.properties().get(EndpointPropertyKeys.URL);
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(uri).process(exchange -> {
                        byte[] body = exchange.getIn().getBody(byte[].class);
                        CloudEvent ce = StreamCloudEventFactory.build(body, d, null,
                            SOURCE, FALLBACK_TYPE);
                        eventCallback.accept(ce);
                    });
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to add Camel route for URI: " + uri, e);
        }
    }
}
