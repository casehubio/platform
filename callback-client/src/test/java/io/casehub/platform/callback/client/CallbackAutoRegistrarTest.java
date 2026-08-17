package io.casehub.platform.callback.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallbackAutoRegistrarTest {

    @Test
    void toKebabCase_simpleName() {
        assertThat(CallbackAutoRegistrar.toKebabCase("WorkerProvisioner"))
                .isEqualTo("worker-provisioner");
    }

    @Test
    void toKebabCase_acronym() {
        assertThat(CallbackAutoRegistrar.toKebabCase("SLABreachPolicy"))
                .isEqualTo("sla-breach-policy");
    }

    @Test
    void toKebabCase_singleWord() {
        assertThat(CallbackAutoRegistrar.toKebabCase("Strategy"))
                .isEqualTo("strategy");
    }

    @Test
    void toKebabCase_empty() {
        assertThat(CallbackAutoRegistrar.toKebabCase("")).isEmpty();
    }

    @Test
    void toKebabCase_null() {
        assertThat(CallbackAutoRegistrar.toKebabCase(null)).isNull();
    }

    @Test
    void noServerUrl_registrationCompletesImmediately() {
        final CallbackAutoRegistrar registrar = new CallbackAutoRegistrar();
        registrar.serverUrl = java.util.Optional.empty();
        registrar.publicUrl = java.util.Optional.empty();
        registrar.init();

        assertThat(registrar.getActiveRegistrations()).isEmpty();
        assertThat(registrar.call().getStatus())
                .isEqualTo(org.eclipse.microprofile.health.HealthCheckResponse.Status.UP);
    }
}
