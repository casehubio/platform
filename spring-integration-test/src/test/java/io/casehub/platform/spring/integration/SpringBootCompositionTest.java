package io.casehub.platform.spring.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.PreferenceStore;
import io.casehub.platform.callback.spring.CallbackSpringAutoConfiguration;
import io.casehub.platform.mcp.spring.McpSpringAutoConfiguration;
import io.casehub.platform.spring.rest.RestControllersAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestStubConfiguration.class)
class SpringBootCompositionTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void jpaStoreBeansRegistered() {
        assertThat(context.getBean("springPreferenceProvider")).isNotNull();
        assertThat(context.getBean(PreferenceStore.class)).isNotNull();
        assertThat(context.getBean("springAccessControlProvider")).isNotNull();
        assertThat(context.getBean("springNotificationStore")).isNotNull();
        assertThat(context.getBean("springSubscriptionStore")).isNotNull();
        assertThat(context.getBean("springDataSourceRegistry")).isNotNull();
        assertThat(context.getBean("springDigestBuffer")).isNotNull();
        assertThat(context.getBean("springDeliveryAttemptStore")).isNotNull();
        assertThat(context.getBean("springSubjectViewStore")).isNotNull();
    }

    @Test
    void healthCheckReturnsUp() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void jacksonBridgeActive() {
        assertThat(objectMapper).isInstanceOf(
                com.fasterxml.jackson.databind.ObjectMapper.class);
        assertThat(objectMapper.getClass().getName())
                .startsWith("com.fasterxml.jackson");
    }
}
