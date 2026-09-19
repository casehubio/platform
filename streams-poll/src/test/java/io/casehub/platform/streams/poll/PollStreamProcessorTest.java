package io.casehub.platform.streams.poll;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollStreamProcessorTest {

    private WireMockServer wireMock;
    private PollStreamProcessorCore core;
    private List<CloudEvent> captured;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        captured = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private EndpointDescriptor descriptor(String url, String streamType) {
        return new EndpointDescriptor(
            Path.of("streams", "sensor-data"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, url,
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, streamType),
            null,
            Set.of(EndpointCapability.QUERY));
    }

    private PollStreamProcessorCore coreWithDescriptors(List<EndpointDescriptor> descriptors) {
        return new PollStreamProcessorCore(new StubRegistry(descriptors), captured::add);
    }

    @Test
    void poll_fetches_and_builds_cloud_event_with_correct_type() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withStatus(200).withBody("{\"temp\":22}")));

        String url = "http://localhost:" + wireMock.port() + "/data";
        var desc = descriptor(url, "io.casehub.sensor.temperature");
        core = coreWithDescriptors(List.of(desc));

        core.poll();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).isEqualTo("io.casehub.sensor.temperature");
    }

    @Test
    void poll_sets_tenancyid_from_descriptor() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withBody("x")));

        String url = "http://localhost:" + wireMock.port() + "/data";
        core = coreWithDescriptors(List.of(descriptor(url, "io.casehub.test")));

        core.poll();

        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void poll_source_is_poll_prefixed() {
        wireMock.stubFor(get(urlEqualTo("/api"))
            .willReturn(aResponse().withBody("x")));

        String url = "http://localhost:" + wireMock.port() + "/api";
        core = coreWithDescriptors(List.of(descriptor(url, "io.casehub.test")));

        core.poll();

        assertThat(captured.get(0).getSource().toString()).startsWith("/platform/streams/poll/");
    }

    @Test
    void poll_withContentType_setsDataContentType() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withBody("x")));

        EndpointDescriptor desc = new EndpointDescriptor(
            Path.of("streams", "sensor-data"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, "http://localhost:" + wireMock.port() + "/data",
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, "io.casehub.sensor.temperature",
                   EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE, "application/json"),
            null,
            Set.of(EndpointCapability.QUERY));
        core = coreWithDescriptors(List.of(desc));

        core.poll();

        assertThat(captured.get(0).getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void poll_withoutContentType_omitsDataContentType() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withBody("x")));

        String url = "http://localhost:" + wireMock.port() + "/data";
        core = coreWithDescriptors(List.of(descriptor(url, "io.casehub.test")));

        core.poll();

        assertThat(captured.get(0).getDataContentType()).isNull();
    }

    @Test
    void fetchBytes_returns_body_on_2xx() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withStatus(200).withBody("{\"temp\":22}")));

        core = coreWithDescriptors(List.of());
        String url = "http://localhost:" + wireMock.port() + "/data";
        byte[] body = core.fetchBytes(url);

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("{\"temp\":22}");
    }

    @Test
    void fetchBytes_throws_IOException_on_non_2xx() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        core = coreWithDescriptors(List.of());
        String url = "http://localhost:" + wireMock.port() + "/data";

        assertThatThrownBy(() -> core.fetchBytes(url))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("503");
    }

    @Test
    void fetchBytes_throws_IOException_on_404() {
        wireMock.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withStatus(404)));

        core = coreWithDescriptors(List.of());
        String url = "http://localhost:" + wireMock.port() + "/data";

        assertThatThrownBy(() -> core.fetchBytes(url))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("404");
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
