package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationTimeoutExceptionTest {

    @Test
    void extendsTimeoutException() {
        var ex = new CorrelationTimeoutException("order-123", Duration.ofSeconds(5));
        assertThat(ex).isInstanceOf(TimeoutException.class);
    }

    @Test
    void carriesKeyAndDuration() {
        var ex = new CorrelationTimeoutException("order-123", Duration.ofSeconds(5));
        assertThat(ex.correlationKey()).isEqualTo("order-123");
        assertThat(ex.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void messageIncludesKeyAndDuration() {
        var ex = new CorrelationTimeoutException(42, Duration.ofMillis(500));
        assertThat(ex.getMessage()).contains("42").contains("PT0.5S");
    }

    @Test
    void keyStoredAsObject_castableToOriginalType() {
        var ex = new CorrelationTimeoutException(42, Duration.ofSeconds(1));
        assertThat((Integer) ex.correlationKey()).isEqualTo(42);
    }
}
