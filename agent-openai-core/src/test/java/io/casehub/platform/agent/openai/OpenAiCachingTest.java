package io.casehub.platform.agent.openai;

import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCachingTest {

    @Test
    void storeDefaultIsFalse() {
        var backend = new AbstractOpenAiSdkBackend(0, config -> Multi.createFrom().empty()) {
            @Override
            protected com.openai.client.OpenAIClient openAiClient() {return null;}

            @Override
            protected Duration defaultTimeout()                     {return Duration.ofSeconds(30);}

            @Override
            protected String defaultModel()                         {return "gpt-4.1";}

            @Override
            public String key()                                     {return "test";}

            @Override
            public String instanceId()                              {return "test-1";}
        };
        assertThat(backend.store()).isFalse();
    }

    @Test
    void storeCanBeOverridden() {
        var backend = new AbstractOpenAiSdkBackend(0, config -> Multi.createFrom().empty()) {
            @Override
            protected com.openai.client.OpenAIClient openAiClient() {return null;}

            @Override
            protected Duration defaultTimeout()                     {return Duration.ofSeconds(30);}

            @Override
            protected String defaultModel()                         {return "gpt-4.1";}

            @Override
            protected boolean store()                               {return true;}

            @Override
            public String key()                                     {return "test";}

            @Override
            public String instanceId()                              {return "test-1";}
        };
        assertThat(backend.store()).isTrue();
    }
}
