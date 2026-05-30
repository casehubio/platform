package io.casehub.platform.scim;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class ScimWireMockResource implements QuarkusTestResourceLifecycleManager {

    static volatile WireMockServer INSTANCE;
    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        INSTANCE = server;
        return Map.of("quarkus.rest-client.scim.url", "http://localhost:" + server.port());
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }
}
