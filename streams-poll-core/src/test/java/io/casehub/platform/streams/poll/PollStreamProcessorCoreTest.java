package io.casehub.platform.streams.poll;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class PollStreamProcessorCoreTest {

    @Test
    void poll_fetches_and_fires_cloud_event(WireMockRuntimeInfo wm) {
        stubFor(get("/data").willReturn(aResponse().withBody("response-body")));

        String url = wm.getHttpBaseUrl() + "/data";
        var descriptor = new EndpointDescriptor(Path.of("/poll-test"),
            TenancyConstants.DEFAULT_TENANT_ID, EndpointType.SERVICE,
            EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, url,
                EndpointPropertyKeys.STREAM_EVENT_TYPE, "data.polled"),
            null, Set.of(EndpointCapability.QUERY));

        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubEndpointRegistry(List.of(descriptor));
        var core = new PollStreamProcessorCore(registry, captured::add);

        core.poll();

        assertThat(captured).hasSize(1);
        CloudEvent ce = captured.get(0);
        assertThat(ce.getType()).isEqualTo("data.polled");
        assertThat(new String(ce.getData().toBytes())).isEqualTo("response-body");
    }

    @Test
    void poll_continues_on_failure(WireMockRuntimeInfo wm) {
        stubFor(get("/fail").willReturn(aResponse().withStatus(500)));
        stubFor(get("/ok").willReturn(aResponse().withBody("ok-data")));

        var failDesc = descriptor(wm.getHttpBaseUrl() + "/fail", "evt.fail");
        var okDesc = descriptor(wm.getHttpBaseUrl() + "/ok", "evt.ok");

        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubEndpointRegistry(List.of(failDesc, okDesc));
        var core = new PollStreamProcessorCore(registry, captured::add);

        core.poll();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).isEqualTo("evt.ok");
    }

    @Test
    void poll_with_no_endpoints_does_nothing() {
        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubEndpointRegistry(List.of());
        var core = new PollStreamProcessorCore(registry, captured::add);

        core.poll();

        assertThat(captured).isEmpty();
    }

    private EndpointDescriptor descriptor(String url, String eventType) {
        return new EndpointDescriptor(Path.of("/test"), TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SERVICE, EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, url,
                EndpointPropertyKeys.STREAM_EVENT_TYPE, eventType),
            null, Set.of(EndpointCapability.QUERY));
    }

    static class StubEndpointRegistry implements EndpointRegistry {
        private final List<EndpointDescriptor> descriptors;

        StubEndpointRegistry(List<EndpointDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        @Override
        public List<EndpointDescriptor> discover(EndpointQuery query) {
            return descriptors;
        }

        @Override public void register(EndpointDescriptor d) {}
        @Override public Optional<EndpointDescriptor> resolve(Path path, String tenancyId) {
            return Optional.empty();
        }
        @Override public void deregister(Path path, String tenancyId) {}
    }
}
