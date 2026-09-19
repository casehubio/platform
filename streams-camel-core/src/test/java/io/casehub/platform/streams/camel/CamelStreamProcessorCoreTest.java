package io.casehub.platform.streams.camel;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.cloudevents.CloudEvent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CamelStreamProcessorCoreTest {

    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    void init_discovers_and_adds_routes() {
        var desc = camelDescriptor("direct:input-1");
        var registry = new StubRegistry(List.of(desc));
        List<CloudEvent> captured = new ArrayList<>();
        var core = new CamelStreamProcessorCore(camelContext, registry, captured::add);

        core.init();

        assertThat(camelContext.getRoutes()).hasSize(1);
    }

    @Test
    void on_endpoint_registered_adds_route_at_runtime() {
        var registry = new StubRegistry(List.of());
        List<CloudEvent> captured = new ArrayList<>();
        var core = new CamelStreamProcessorCore(camelContext, registry, captured::add);

        core.init();
        assertThat(camelContext.getRoutes()).isEmpty();

        core.onEndpointRegistered(camelDescriptor("direct:runtime-1"));
        assertThat(camelContext.getRoutes()).hasSize(1);
    }

    @Test
    void idempotent_route_registration() {
        var desc = camelDescriptor("direct:idem");
        var registry = new StubRegistry(List.of(desc));
        List<CloudEvent> captured = new ArrayList<>();
        var core = new CamelStreamProcessorCore(camelContext, registry, captured::add);

        core.init();
        core.onEndpointRegistered(camelDescriptor("direct:idem"));

        assertThat(camelContext.getRoutes()).hasSize(1);
    }

    @Test
    void filters_non_camel_protocol() {
        var registry = new StubRegistry(List.of());
        List<CloudEvent> captured = new ArrayList<>();
        var core = new CamelStreamProcessorCore(camelContext, registry, captured::add);
        core.init();

        var httpDesc = new EndpointDescriptor(Path.of("/http-test"),
            TenancyConstants.DEFAULT_TENANT_ID, EndpointType.SYSTEM, EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, "direct:should-not-route"),
            null, Set.of(EndpointCapability.RECEIVE));
        core.onEndpointRegistered(httpDesc);

        assertThat(camelContext.getRoutes()).isEmpty();
    }

    @Test
    void pre_startup_events_ignored() {
        var registry = new StubRegistry(List.of());
        List<CloudEvent> captured = new ArrayList<>();
        var core = new CamelStreamProcessorCore(camelContext, registry, captured::add);

        core.onEndpointRegistered(camelDescriptor("direct:early"));

        assertThat(camelContext.getRoutes()).isEmpty();
    }

    private EndpointDescriptor camelDescriptor(String uri) {
        return new EndpointDescriptor(Path.of("/camel-test"),
            TenancyConstants.DEFAULT_TENANT_ID, EndpointType.SYSTEM, EndpointProtocol.CAMEL,
            Map.of(EndpointPropertyKeys.URL, uri,
                EndpointPropertyKeys.STREAM_EVENT_TYPE, "camel.event"),
            null, Set.of(EndpointCapability.RECEIVE));
    }

    static class StubRegistry implements EndpointRegistry {
        private final List<EndpointDescriptor> descriptors;
        StubRegistry(List<EndpointDescriptor> descriptors) { this.descriptors = descriptors; }
        @Override public List<EndpointDescriptor> discover(EndpointQuery q) { return descriptors; }
        @Override public void register(EndpointDescriptor d) {}
        @Override public Optional<EndpointDescriptor> resolve(Path p, String t) { return Optional.empty(); }
        @Override public void deregister(Path p, String t) {}
    }
}
