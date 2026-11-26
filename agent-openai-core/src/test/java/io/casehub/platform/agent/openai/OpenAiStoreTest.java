package io.casehub.platform.agent.openai;

import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStoreTest {

    @Test
    void buildParams_withStoreEnabled_setsStoreTrue() {
        var backend = new TestableOpenAiBackend(true);
        var config = AgentSessionConfig.of("system", "user");

        var params = backend.buildParams(config);

        assertThat(params.store()).isPresent().contains(true);
    }

    @Test
    void buildParams_withStoreDisabled_noStoreParam() {
        var backend = new TestableOpenAiBackend(false);
        var config = AgentSessionConfig.of("system", "user");

        var params = backend.buildParams(config);

        assertThat(params.store()).isEmpty();
    }

    private static class TestableOpenAiBackend extends AbstractOpenAiSdkBackend {

        private final boolean storeEnabled;

        TestableOpenAiBackend(boolean storeEnabled) {
            super(1, null);
            this.storeEnabled = storeEnabled;
        }

        @Override public String key() { return "test-openai"; }
        @Override protected com.openai.client.OpenAIClient openAiClient() {
            throw new UnsupportedOperationException("test stub");
        }
        @Override protected Duration defaultTimeout() { return Duration.ofSeconds(30); }
        @Override protected String defaultModel() { return "gpt-4.1"; }
        @Override protected boolean store() { return storeEnabled; }
    }
}
