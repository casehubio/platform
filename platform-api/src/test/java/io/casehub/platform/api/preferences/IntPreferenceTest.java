package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IntPreferenceTest {

    @Test
    void shouldStoreValue() {
        IntPreference pref = new IntPreference(5);
        assertThat(pref.value()).isEqualTo(5);
    }

    @Test
    void shouldCreateViaFactory() {
        assertThat(IntPreference.of(3)).isEqualTo(new IntPreference(3));
    }

    @Test
    void shouldParseFromString() {
        IntPreference pref = IntPreference.parse("7");
        assertThat(pref.value()).isEqualTo(7);
    }

    @Test
    void shouldRejectNullParse() {
        assertThatThrownBy(() -> IntPreference.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHaveValueSemantics() {
        assertThat(new IntPreference(3)).isEqualTo(new IntPreference(3));
        assertThat(new IntPreference(3).hashCode()).isEqualTo(new IntPreference(3).hashCode());
    }
}
