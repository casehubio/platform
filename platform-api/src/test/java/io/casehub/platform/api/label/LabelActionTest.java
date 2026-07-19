package io.casehub.platform.api.label;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelActionTest {

    @Test
    void add_storesLabel() {
        var add = new LabelAction.Add("priority/high");
        assertThat(add.label()).isEqualTo("priority/high");
    }

    @Test
    void remove_storesLabel() {
        var remove = new LabelAction.Remove("priority/high");
        assertThat(remove.label()).isEqualTo("priority/high");
    }

    @Test
    void add_nullLabel_throws() {
        assertThatThrownBy(() -> new LabelAction.Add(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("label");
    }

    @Test
    void remove_nullLabel_throws() {
        assertThatThrownBy(() -> new LabelAction.Remove(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("label");
    }

    @Test
    void add_blankLabel_throws() {
        assertThatThrownBy(() -> new LabelAction.Add("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void remove_blankLabel_throws() {
        assertThatThrownBy(() -> new LabelAction.Remove("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void add_emptyLabel_throws() {
        assertThatThrownBy(() -> new LabelAction.Add(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sealedInterface_labelAccessor() {
        LabelAction add = new LabelAction.Add("queue/urgent");
        LabelAction remove = new LabelAction.Remove("queue/urgent");
        assertThat(add.label()).isEqualTo("queue/urgent");
        assertThat(remove.label()).isEqualTo("queue/urgent");
    }

    @Test
    void add_isInstanceOfLabelAction() {
        assertThat(new LabelAction.Add("x")).isInstanceOf(LabelAction.class);
    }

    @Test
    void remove_isInstanceOfLabelAction() {
        assertThat(new LabelAction.Remove("x")).isInstanceOf(LabelAction.class);
    }
}
