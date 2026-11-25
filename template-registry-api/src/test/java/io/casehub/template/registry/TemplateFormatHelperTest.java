package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFormatHelperTest {

    @Test
    void formatsSimplePattern() {
        String format = TemplateFormatHelper.formatSuggestion("I ${decision} this ${subject}");
        assertThat(format).isEqualTo("I [decision] this [subject]");
    }

    @Test
    void stripsOptionalBrackets() {
        String format = TemplateFormatHelper.formatSuggestion(
            "I ${decision} this ${subject}[ because ${rationale}]");
        assertThat(format).isEqualTo("I [decision] this [subject] because [rationale]");
    }

    @Test
    void noVariablesPassesThrough() {
        String format = TemplateFormatHelper.formatSuggestion("hello world");
        assertThat(format).isEqualTo("hello world");
    }
}
