package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRegistryTest {

    @Test
    void matchesFirstRegisteredTemplate() {
        var registry = TemplateRegistry.builder()
                .add("vote", "I ${decision} this design because ${rationale}")
                .add("escalate", "I escalate to ${target} because ${reason}")
                .build();

        var result = registry.match("I approve this design because the error handling is solid");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("vote");
        assertThat(result.get().values()).containsEntry("decision", "approve");
        assertThat(result.get().values()).containsEntry("rationale", "the error handling is solid");
    }

    @Test
    void matchesSecondTemplateWhenFirstDoesNotMatch() {
        var registry = TemplateRegistry.builder()
                .add("vote", "I ${decision} this design because ${rationale}")
                .add("escalate", "I escalate to ${target} because ${reason}")
                .build();

        var result = registry.match("I escalate to the architecture board because scope changed");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("escalate");
        assertThat(result.get().values()).containsEntry("target", "the architecture board");
        assertThat(result.get().values()).containsEntry("reason", "scope changed");
    }

    @Test
    void returnsEmptyWhenNoTemplateMatches() {
        var registry = TemplateRegistry.builder()
                .add("vote", "I ${decision} this design because ${rationale}")
                .build();

        var result = registry.match("completely unrelated text");
        assertThat(result).isEmpty();
    }

    @Test
    void firstMatchWinsWhenMultipleCouldMatch() {
        var registry = TemplateRegistry.builder()
                .add("specific", "I approve because ${rationale}")
                .add("general", "I ${decision} because ${rationale}")
                .build();

        var result = registry.match("I approve because it looks good");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("specific");
    }

    @Test
    void emptyRegistryMatchesNothing() {
        var registry = TemplateRegistry.builder().build();
        assertThat(registry.match("anything")).isEmpty();
    }

    @Test
    void templateNamesAccessor() {
        var registry = TemplateRegistry.builder()
                .add("vote", "I ${decision}")
                .add("escalate", "escalate to ${target}")
                .build();

        assertThat(registry.templateNames()).containsExactly("vote", "escalate");
    }

    @Test
    void rejectsDuplicateTemplateName() {
        assertThatThrownBy(() -> TemplateRegistry.builder()
                .add("vote", "I ${decision}")
                .add("vote", "I ${other}")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullNameOrTemplate() {
        assertThatThrownBy(() -> TemplateRegistry.builder().add(null, "template"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TemplateRegistry.builder().add("name", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void matchRejectsNullInput() {
        var registry = TemplateRegistry.builder().build();
        assertThatThrownBy(() -> registry.match(null))
                .isInstanceOf(NullPointerException.class);
    }
}
