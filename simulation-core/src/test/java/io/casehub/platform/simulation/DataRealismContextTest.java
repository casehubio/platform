package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataRealismContextTest {

    @Test
    void initialStateIsNull() {
        var context = new DataRealismContext();
        assertThat(context.current()).isNull();
        assertThat(context.dataRealism()).isNull();
    }

    @Test
    void setRecordsObservation() {
        var context = new DataRealismContext();
        context.set("acl.canAccess", DataRealism.DOMAIN_PLAUSIBLE);

        assertThat(context.current()).isNotNull();
        assertThat(context.current().qualifiedName()).isEqualTo("acl.canAccess");
        assertThat(context.current().dataRealism()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
        assertThat(context.dataRealism()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
    }

    @Test
    void lastWriteWins() {
        var context = new DataRealismContext();
        context.set("acl.canAccess", DataRealism.DOMAIN_PLAUSIBLE);
        context.set("memory.store", DataRealism.STRUCTURALLY_VALID);

        assertThat(context.current().qualifiedName()).isEqualTo("memory.store");
        assertThat(context.dataRealism()).isEqualTo(DataRealism.STRUCTURALLY_VALID);
    }

    @Test
    void nullDataRealismForDeterministicFallthrough() {
        var context = new DataRealismContext();
        context.set("registry.register", null);

        assertThat(context.current()).isNotNull();
        assertThat(context.current().qualifiedName()).isEqualTo("registry.register");
        assertThat(context.dataRealism()).isNull();
    }
}
