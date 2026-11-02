package io.casehub.platform.api.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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

    @Test
    void jacksonRoundTrip_acknowledge() throws Exception {
        var mapper       = new com.fasterxml.jackson.databind.ObjectMapper();
        var hint         = ReplyHint.acknowledge();
        var json         = mapper.writeValueAsString(hint);
        var deserialized = mapper.readValue(json, ReplyHint.class);
        assertThat(deserialized.type()).isEqualTo(ReplyType.ACKNOWLEDGE);
        assertThat(deserialized.options()).isNull();
    }

    @Test
    void jacksonRoundTrip_freeText() throws Exception {
        var mapper       = new com.fasterxml.jackson.databind.ObjectMapper();
        var hint         = ReplyHint.freeText();
        var json         = mapper.writeValueAsString(hint);
        var deserialized = mapper.readValue(json, ReplyHint.class);
        assertThat(deserialized.type()).isEqualTo(ReplyType.FREE_TEXT);
        assertThat(deserialized.options()).isNull();
    }

    @Test
    void jacksonRoundTrip_options() throws Exception {
        var mapper       = new com.fasterxml.jackson.databind.ObjectMapper();
        var hint         = ReplyHint.options(List.of("approve", "reject", "defer"));
        var json         = mapper.writeValueAsString(hint);
        var deserialized = mapper.readValue(json, ReplyHint.class);
        assertThat(deserialized.type()).isEqualTo(ReplyType.OPTIONS);
        assertThat(deserialized.options()).containsExactly("approve", "reject", "defer");
    }
}
