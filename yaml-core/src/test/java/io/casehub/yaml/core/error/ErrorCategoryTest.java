package io.casehub.yaml.core.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoryTest {

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void yamlKey_isKebabCase(ErrorCategory cat) {
        String key = cat.yamlKey();
        assertThat(key).isNotNull();
        assertThat(key).matches("[a-z][a-z0-9-]*");
    }

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void fromYamlKey_roundTrips(ErrorCategory cat) {
        assertThat(ErrorCategory.fromYamlKey(cat.yamlKey())).isEqualTo(cat);
    }

    @Test
    void fromYamlKey_unknownReturnsNull() {
        assertThat(ErrorCategory.fromYamlKey("nonexistent")).isNull();
    }

    @Test
    void fromYamlKey_nullReturnsNull() {
        assertThat(ErrorCategory.fromYamlKey(null)).isNull();
    }

    @Test
    void specificMappings() {
        assertThat(ErrorCategory.TIMEOUT.yamlKey()).isEqualTo("timeout");
        assertThat(ErrorCategory.STEP_ACTION_FAILED.yamlKey()).isEqualTo("action-failed");
        assertThat(ErrorCategory.RATE_LIMIT.yamlKey()).isEqualTo("rate-limit");
        assertThat(ErrorCategory.AUTH_DENIED.yamlKey()).isEqualTo("auth-denied");
        assertThat(ErrorCategory.PARAMETER_VIOLATION.yamlKey()).isEqualTo("parameter-violation");
    }
}
