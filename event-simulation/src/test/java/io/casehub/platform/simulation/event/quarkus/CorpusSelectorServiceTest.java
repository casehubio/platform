package io.casehub.platform.simulation.event.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class CorpusSelectorServiceTest {

    @Inject
    CorpusSelectorService service;

    @Test
    void listReturnsRegisteredSelectors() {
        var selectors = service.list();
        assertThat(selectors).isNotEmpty();
        assertThat(selectors).anyMatch(s -> s.qualifiedName().equals("test-spi.invoke"));
    }

    @Test
    void statusReturnsSpecificSelector() {
        var status = service.status("test-spi.invoke");
        assertThat(status.qualifiedName()).isEqualTo("test-spi.invoke");
        assertThat(status.type()).isEqualTo("state-machine");
        assertThat(status.activePhase()).isEqualTo("idle");
    }

    @Test
    void statusThrowsForUnknown() {
        assertThatThrownBy(() -> service.status("nonexistent"))
                .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
    }

    @Test
    void advanceTransitionsStateMachine() {
        service.reset("test-spi.invoke");
        var result = service.advance(
                new CorpusSelectorAdvanceRequest("test-spi.invoke", "activate"));
        assertThat(result.activePhase()).isEqualTo("active");

        service.reset("test-spi.invoke");
    }

    @Test
    void advanceValidatesInput() {
        assertThatThrownBy(() -> service.advance(
                new CorpusSelectorAdvanceRequest(null, "trigger")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.advance(
                new CorpusSelectorAdvanceRequest("qn", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetRestoresInitialPhase() {
        service.advance(new CorpusSelectorAdvanceRequest("test-spi.invoke", "activate"));
        assertThat(service.status("test-spi.invoke").activePhase()).isEqualTo("active");

        var result = service.reset("test-spi.invoke");
        assertThat(result.activePhase()).isEqualTo("idle");
    }

    @Test
    void resetThrowsForUnknown() {
        assertThatThrownBy(() -> service.reset("nonexistent"))
                .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
    }
}
