package io.casehub.plugin.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PluginRequirementsTest {

    @Test
    void none_hasEmptyDefaults() {
        var none = PluginRequirements.NONE;
        assertThat(none.runtimeVersionRange()).isEmpty();
        assertThat(none.primitives()).isEmpty();
        assertThat(none.types()).isEmpty();
    }

    @Test
    void defensiveCopy_preventsModification() {
        var mutableList = new ArrayList<>(List.of("rest-request"));
        var reqs = new PluginRequirements(Optional.empty(), mutableList, List.of());
        mutableList.add("should-not-appear");
        assertThat(reqs.primitives()).containsExactly("rest-request");
    }

    @Test
    void nullDefaults() {
        var reqs = new PluginRequirements(null, null, null);
        assertThat(reqs.runtimeVersionRange()).isEmpty();
        assertThat(reqs.primitives()).isEmpty();
        assertThat(reqs.types()).isEmpty();
    }
}
