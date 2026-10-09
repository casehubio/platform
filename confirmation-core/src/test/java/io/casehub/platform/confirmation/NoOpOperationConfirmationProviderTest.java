package io.casehub.platform.confirmation;

import io.casehub.platform.api.confirmation.ConfirmationRequiredException;
import io.casehub.platform.api.confirmation.OperationDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpOperationConfirmationProviderTest {

    @Test
    void confirm_alwaysThrows() {
        var provider = new NoOpOperationConfirmationProvider();
        var desc = new OperationDescriptor("Test", Map.of());

        assertThatThrownBy(() -> provider.confirm("actor", "tenant", desc))
            .isInstanceOf(ConfirmationRequiredException.class);
    }
}
