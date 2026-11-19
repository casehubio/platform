package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void withTemperature_negative_throws() {
        assertThatThrownBy(() -> ModelParameters.of().withTemperature(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    void withTemperature_aboveMax_throws() {
        assertThatThrownBy(() -> ModelParameters.of().withTemperature(2.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    void withMaxTokens_zero_throws() {
        assertThatThrownBy(() -> ModelParameters.of().withMaxTokens(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens");
    }

    @Test
    void withTopP_aboveOne_throws() {
        assertThatThrownBy(() -> ModelParameters.of().withTopP(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topP");
    }

    @Test
    void withTopK_negative_throws() {
        assertThatThrownBy(() -> ModelParameters.of().withTopK(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void withTemperature_boundaryValues_accepted() {
        assertThat(ModelParameters.of().withTemperature(0).temperature()).isEqualTo(0.0);
        assertThat(ModelParameters.of().withTemperature(2).temperature()).isEqualTo(2.0);
    }

    @Test
    void withTopP_boundaryValues_accepted() {
        assertThat(ModelParameters.of().withTopP(0).topP()).isEqualTo(0.0);
        assertThat(ModelParameters.of().withTopP(1).topP()).isEqualTo(1.0);
    }

}
