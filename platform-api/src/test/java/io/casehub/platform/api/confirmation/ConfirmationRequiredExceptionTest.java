package io.casehub.platform.api.confirmation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationRequiredExceptionTest {

    @Test
    void denied_hasCorrectMessageAndAccessors() {
        var desc = new OperationDescriptor("Payment of £50", Map.of("amount", "50"));
        var ex = new ConfirmationRequiredException(desc, ConfirmationResult.DENIED);

        assertThat(ex.getMessage()).contains("denied").contains("Payment of £50");
        assertThat(ex.operation()).isEqualTo(desc);
        assertThat(ex.result()).isEqualTo(ConfirmationResult.DENIED);
    }

    @Test
    void expired_hasCorrectMessage() {
        var desc = new OperationDescriptor("Delete account", Map.of());
        var ex = new ConfirmationRequiredException(desc, ConfirmationResult.EXPIRED);

        assertThat(ex.getMessage()).contains("expired");
    }
}
