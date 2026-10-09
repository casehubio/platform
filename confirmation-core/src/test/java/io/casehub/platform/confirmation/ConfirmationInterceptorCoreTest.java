package io.casehub.platform.confirmation;

import io.casehub.platform.api.confirmation.ConfirmationRequiredException;
import io.casehub.platform.api.confirmation.ConfirmationResult;
import io.casehub.platform.api.confirmation.OperationDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationInterceptorCoreTest {

    @Test
    void confirmed_doesNotThrow() {
        var core = new ConfirmationInterceptorCore(
            (actorId, tenancyId, op) -> ConfirmationResult.CONFIRMED);
        var desc = new OperationDescriptor("Test op", Map.of());

        assertThatCode(() -> core.requireConfirmation("actor1", "tenant1", desc))
            .doesNotThrowAnyException();
    }

    @Test
    void denied_throwsConfirmationRequired() {
        var core = new ConfirmationInterceptorCore(
            (actorId, tenancyId, op) -> ConfirmationResult.DENIED);
        var desc = new OperationDescriptor("Test op", Map.of());

        assertThatThrownBy(() -> core.requireConfirmation("actor1", "tenant1", desc))
            .isInstanceOf(ConfirmationRequiredException.class)
            .satisfies(ex -> {
                var cre = (ConfirmationRequiredException) ex;
                assertThat(cre.result()).isEqualTo(ConfirmationResult.DENIED);
                assertThat(cre.operation()).isEqualTo(desc);
            });
    }

    @Test
    void expired_throwsConfirmationRequired() {
        var core = new ConfirmationInterceptorCore(
            (actorId, tenancyId, op) -> ConfirmationResult.EXPIRED);
        var desc = new OperationDescriptor("Test op", Map.of());

        assertThatThrownBy(() -> core.requireConfirmation("actor1", "tenant1", desc))
            .isInstanceOf(ConfirmationRequiredException.class)
            .satisfies(ex -> {
                var cre = (ConfirmationRequiredException) ex;
                assertThat(cre.result()).isEqualTo(ConfirmationResult.EXPIRED);
            });
    }
}
