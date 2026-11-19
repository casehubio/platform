package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelParametersTest {

    @Test
    void of_allFieldsNull() {
        var params = ModelParameters.of();
        assertThat(params.temperature()).isNull();
        assertThat(params.maxTokens()).isNull();
        assertThat(params.topP()).isNull();
        assertThat(params.topK()).isNull();
    }

    @Test
    void withTemperature_setsOnlyTemperature() {
        var params = ModelParameters.of().withTemperature(0.7);
        assertThat(params.temperature()).isEqualTo(0.7);
        assertThat(params.maxTokens()).isNull();
    }

    @Test
    void withMaxTokens_setsOnlyMaxTokens() {
        var params = ModelParameters.of().withMaxTokens(2048);
        assertThat(params.maxTokens()).isEqualTo(2048);
        assertThat(params.temperature()).isNull();
    }

    @Test
    void withTopP_setsOnlyTopP() {
        var params = ModelParameters.of().withTopP(0.9);
        assertThat(params.topP()).isEqualTo(0.9);
    }

    @Test
    void withTopK_setsOnlyTopK() {
        var params = ModelParameters.of().withTopK(40);
        assertThat(params.topK()).isEqualTo(40);
    }

    @Test
    void chaining_preservesPriorFields() {
        var params = ModelParameters.of()
                .withTemperature(0.5)
                .withMaxTokens(1024)
                .withTopP(0.95)
                .withTopK(50);
        assertThat(params.temperature()).isEqualTo(0.5);
        assertThat(params.maxTokens()).isEqualTo(1024);
        assertThat(params.topP()).isEqualTo(0.95);
        assertThat(params.topK()).isEqualTo(50);
    }
}
