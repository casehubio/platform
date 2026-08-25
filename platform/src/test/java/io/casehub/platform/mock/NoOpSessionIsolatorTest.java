package io.casehub.platform.mock;

import io.casehub.platform.api.governance.SessionIsolator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpSessionIsolatorTest {

    private final SessionIsolator isolator = new NoOpSessionIsolator();

    @Test
    void runIsolated_supplier_returnsResult() {
        assertThat(isolator.runIsolated(() -> "value")).isEqualTo("value");
    }

    @Test
    void runIsolated_runnable_executes() {
        AtomicBoolean ran = new AtomicBoolean(false);
        isolator.runIsolated(() -> ran.set(true));
        assertThat(ran.get()).isTrue();
    }
}
