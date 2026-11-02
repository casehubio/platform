package io.casehub.platform.api.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReplyHintTest {

    @Test
    void acknowledgeFactoryCreatesCorrectHint() {
        var hint = ReplyHint.acknowledge();
        assertThat(hint.type()).isEqualTo(ReplyType.ACKNOWLEDGE);
        assertThat(hint.options()).isNull();
    }

    @Test
    void freeTextFactoryCreatesCorrectHint() {
        var hint = ReplyHint.freeText();
        assertThat(hint.type()).isEqualTo(ReplyType.FREE_TEXT);
        assertThat(hint.options()).isNull();
    }

    @Test
    void optionsFactoryCreatesCorrectHint() {
        var hint = ReplyHint.options(List.of("approve", "reject"));
        assertThat(hint.type()).isEqualTo(ReplyType.OPTIONS);
        assertThat(hint.options()).containsExactly("approve", "reject");
    }

    @Test
    void optionsFactoryDefensiveCopy() {
        var mutable = new java.util.ArrayList<>(List.of("approve", "reject"));
        var hint = ReplyHint.options(mutable);
        mutable.add("defer");
        assertThat(hint.options()).containsExactly("approve", "reject");
    }

    @Test
    void optionsRejectsNull() {
        assertThatNullPointerException().isThrownBy(() ->
                new ReplyHint(ReplyType.OPTIONS, null));
    }

    @Test
    void optionsRejectsEmpty() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ReplyHint.options(List.of()));
    }

    @Test
    void acknowledgeIgnoresOptions() {
        var hint = new ReplyHint(ReplyType.ACKNOWLEDGE, List.of("ignored"));
        assertThat(hint.options()).isNull();
    }

    @Test
    void rejectsNullType() {
        assertThatNullPointerException().isThrownBy(() ->
                new ReplyHint(null, null));
    }
}
