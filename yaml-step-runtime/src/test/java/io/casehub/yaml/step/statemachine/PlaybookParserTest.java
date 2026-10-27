package io.casehub.yaml.step.statemachine;

import io.casehub.yaml.core.playbook.PlaybookDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookParserTest {

    @Test
    void parsePlaybookFrontMatter_extractsVersionAndSchema() {
        String yaml = """
                playbook: "1.0"
                schema: server
                ---
                scenario: incident-response
                states:
                  start:
                    - next: end
                  end: terminal
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isTrue();
        assertThat(doc.version()).isEqualTo("1.0");
        assertThat(doc.schema()).isEqualTo("server");
        assertThat(doc.frontMatter().name()).isNull();
        assertThat(doc.content()).containsKey("scenario");
        assertThat(doc.content()).containsKey("states");
    }

    @Test
    void parsePlaybookFrontMatter_withName() {
        String yaml = """
                playbook: "1.0"
                schema: client
                name: helpdesk-intake
                ---
                scenario: helpdesk-intake
                steps:
                  - navigate: "#intake"
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.frontMatter().name()).isEqualTo("helpdesk-intake");
        assertThat(doc.schema()).isEqualTo("client");
    }

    @Test
    void parsePlaybookFrontMatter_withMetadata() {
        String yaml = """
                playbook: "1.0"
                schema: clinical-server
                author: test-user
                ---
                scenario: trial
                states:
                  start:
                    - next: end
                  end: terminal
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.schema()).isEqualTo("clinical-server");
        assertThat(doc.frontMatter().metadata()).containsEntry("author", "test-user");
    }

    @Test
    void parseLegacySingleDocument_backwardCompatible() {
        String yaml = """
                scenario: legacy
                states:
                  start:
                    - next: end
                  end: terminal
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isFalse();
        assertThat(doc.schema()).isNull();
        assertThat(doc.content()).containsKey("scenario");
        assertThat(doc.content()).containsKey("states");
    }

    @Test
    void parseLegacyMultiDoc_withoutPlaybookKey_backwardCompatible() {
        String yaml = """
                scenario: test-scenario
                ---
                states:
                  idle:
                    - next: done
                  done: terminal
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isFalse();
        assertThat(doc.content()).containsKey("states");
    }

    @Test
    void parsePlaybookFrontMatter_versionAsNumber() {
        String yaml = """
                playbook: 1.0
                schema: server
                ---
                scenario: test
                states:
                  start:
                    - next: end
                  end: terminal
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.version()).isEqualTo("1.0");
    }

    @Test
    void parseEmptyYaml_throws() {
        assertThatThrownBy(() -> PlaybookParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePlaybook_contentMustBeMap() {
        String yaml = """
                playbook: "1.0"
                schema: server
                ---
                - item1
                - item2
                """;

        assertThatThrownBy(() -> PlaybookParser.parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapping");
    }

    @Test
    void parseThreeDocFormat_mergesStepsFromBareList() {
        String yaml = """
                playbook: "1.0"
                schema: client
                ---
                playbook: barrier-demo
                meta:
                  title: "Barrier"
                orchestration:
                  barriers:
                    all-ready: { count: 3 }
                ---
                - click: { role: button, name: "A" }
                - delay: 1000ms
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isTrue();
        assertThat(doc.version()).isEqualTo("1.0");
        assertThat(doc.schema()).isEqualTo("client");
        assertThat(doc.frontMatter().name()).isEqualTo("barrier-demo");
        assertThat(doc.content()).containsKey("orchestration");
        assertThat(doc.content()).containsKey("steps");
        assertThat(doc.content()).containsKey("meta");
        assertThat(doc.content().get("steps")).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        var steps = (java.util.List<java.util.Map<String, Object>>) doc.content().get("steps");
        assertThat(steps).hasSize(2);
    }

    @Test
    void parseThreeDocFormat_withoutPlaybookNameInDoc1() {
        String yaml = """
                playbook: "1.0"
                schema: client
                ---
                meta:
                  title: "REST"
                  description: "REST binding"
                ---
                - rest-request:
                    method: GET
                    url: /api/users
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isTrue();
        assertThat(doc.schema()).isEqualTo("client");
        assertThat(doc.frontMatter().name()).isNull();
        assertThat(doc.content()).containsKey("meta");
        assertThat(doc.content()).containsKey("steps");
    }

    @Test
    void parseThreeDocFormat_moduleComposition_noStepsKey() {
        String yaml = """
                playbook: "1.0"
                schema: client
                ---
                meta:
                  title: "Modules"
                ---
                name: status-panel
                parameters:
                  title: { type: "STRING", required: true }
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isTrue();
        assertThat(doc.content()).containsKey("meta");
        assertThat(doc.content()).containsKey("name");
        assertThat(doc.content()).containsKey("parameters");
        assertThat(doc.content()).doesNotContainKey("steps");
    }

    @Test
    void parseThreeDocFormat_preservesFrontMatterName() {
        String yaml = """
                playbook: "1.0"
                schema: server
                name: explicit-name
                ---
                playbook: doc1-name
                meta:
                  title: "Test"
                ---
                - log: { message: "hello" }
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.frontMatter().name()).isEqualTo("explicit-name");
    }
}
