package io.casehub.platform.api.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRefTest {

    @Test
    void forTier_flagship_returnsPrefixedString() {
        assertThat(ModelRef.forTier(ModelTier.FLAGSHIP)).isEqualTo("tier:FLAGSHIP");
    }

    @Test
    void forTier_fast_returnsPrefixedString() {
        assertThat(ModelRef.forTier(ModelTier.FAST)).isEqualTo("tier:FAST");
    }

    @Test
    void forTier_standard_returnsPrefixedString() {
        assertThat(ModelRef.forTier(ModelTier.STANDARD)).isEqualTo("tier:STANDARD");
    }

    @Test
    void forTier_embedding_returnsPrefixedString() {
        assertThat(ModelRef.forTier(ModelTier.EMBEDDING)).isEqualTo("tier:EMBEDDING");
    }

    @Test
    void forTier_null_throwsNpe() {
        assertThatThrownBy(() -> ModelRef.forTier(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isTierRef_validPrefix_returnsTrue() {
        assertThat(ModelRef.isTierRef("tier:FLAGSHIP")).isTrue();
    }

    @Test
    void isTierRef_modelId_returnsFalse() {
        assertThat(ModelRef.isTierRef("claude-sonnet-5")).isFalse();
    }

    @Test
    void isTierRef_null_returnsFalse() {
        assertThat(ModelRef.isTierRef(null)).isFalse();
    }

    @Test
    void isTierRef_emptyAfterPrefix_returnsTrue() {
        assertThat(ModelRef.isTierRef("tier:")).isTrue();
    }

    @Test
    void parseTier_flagship_returnsEnum() {
        assertThat(ModelRef.parseTier("tier:FLAGSHIP")).isEqualTo(ModelTier.FLAGSHIP);
    }

    @Test
    void parseTier_fast_returnsEnum() {
        assertThat(ModelRef.parseTier("tier:FAST")).isEqualTo(ModelTier.FAST);
    }

    @Test
    void parseTier_invalid_throwsIllegalArgument() {
        assertThatThrownBy(() -> ModelRef.parseTier("tier:INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseTier_emptyAfterPrefix_throwsIllegalArgument() {
        assertThatThrownBy(() -> ModelRef.parseTier("tier:"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTrip_allTiers() {
        for (ModelTier tier : ModelTier.values()) {
            String ref = ModelRef.forTier(tier);
            assertThat(ModelRef.isTierRef(ref)).isTrue();
            assertThat(ModelRef.parseTier(ref)).isEqualTo(tier);
        }
    }
}
