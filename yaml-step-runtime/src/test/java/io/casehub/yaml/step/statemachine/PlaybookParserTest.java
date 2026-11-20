package io.casehub.yaml.step.statemachine;

import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
                ---
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
    void parseVersionedPlaybook_rejectsStepsWrapper_twoDocFormat() {
        String yaml = """
                playbook: "1.0"
                schema: client
                ---
                steps:
                  - navigate: "#intake"
                """;

        assertThatThrownBy(() -> PlaybookParser.parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'steps:' wrapper key is not allowed");
    }

    @Test
    void parseVersionedPlaybook_rejectsStepsWrapper_threeDocFormat() {
        String yaml = """
                playbook: "1.0"
                schema: client
                ---
                meta:
                  title: "test"
                steps:
                  - navigate: "#intake"
                ---
                - unreachable: step
                """;

        assertThatThrownBy(() -> PlaybookParser.parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'steps:' wrapper key is not allowed");
    }

    @Test
    void parseLegacySingleDoc_rejectsStepsKey() {
        String yaml = """
                      steps:
                        - log: hello
                      """;

        assertThatThrownBy(() -> PlaybookParser.parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'steps:' wrapper key is not allowed");
    }

    @Test
    void parseTwoDocFormat_bareListAsSteps() {
        String yaml = """
                playbook: "1.0"
                schema: server
                ---
                - click: "A"
                - click: "B"
                """;

        PlaybookDocument doc = PlaybookParser.parse(yaml);

        assertThat(doc.hasPlaybookHeader()).isTrue();
        assertThat(doc.schema()).isEqualTo("server");
        assertThat(doc.content()).containsKey("steps");
        @SuppressWarnings("unchecked")
        var steps = (java.util.List<java.util.Map<String, Object>>) doc.content().get("steps");
        assertThat(steps).hasSize(2);
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

    @Nested
    class LocationTracking {

        @Test
        void tracksStepListLocations() {
            String yaml = """
                          name: test
                          ---
                          - log: hello
                          - process:
                              command: run.sh
                          """;
            var result = PlaybookParser.parseWithLocations(yaml, "test.yaml");
            assertThat(result.locations().isEmpty()).isFalse();

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) result.document().content().get("steps");
            assertThat(steps).hasSize(2);

            SourceLocation loc0 = result.locations().locationOf(steps.get(0));
            assertThat(loc0.file()).isEqualTo("test.yaml");
            assertThat(loc0.line()).isEqualTo(3);
            assertThat(loc0.isKnown()).isTrue();

            SourceLocation loc1 = result.locations().locationOf(steps.get(1));
            assertThat(loc1.file()).isEqualTo("test.yaml");
            assertThat(loc1.line()).isEqualTo(4);
        }

        @Test
        void tracksRootContentLocation() {
            String yaml = """
                          scenario: test
                          """;
            var            result  = PlaybookParser.parseWithLocations(yaml, "test.yaml");
            SourceLocation rootLoc = result.locations().locationOf(result.document().content());
            assertThat(rootLoc.line()).isEqualTo(1);
            assertThat(rootLoc.column()).isEqualTo(1);
        }

        @Test
        void unknownObjectReturnsFallback() {
            String yaml = """
                          name: test
                          ---
                          - log: hello
                          """;
            var            result = PlaybookParser.parseWithLocations(yaml, "test.yaml");
            SourceLocation loc    = result.locations().locationOf(Map.of("unrelated", "map"), "fallback.yaml");
            assertThat(loc.file()).isEqualTo("fallback.yaml");
            assertThat(loc.line()).isEqualTo(-1);
        }

        @Test
        void resolvesScalarTypes() {
            String yaml = """
                          count: 42
                          enabled: true
                          ratio: 3.14
                          label: hello
                          """;
            var result  = PlaybookParser.parseWithLocations(yaml, null);
            var content = result.document().content();
            assertThat(content.get("count")).isEqualTo(42);
            assertThat(content.get("enabled")).isEqualTo(true);
            assertThat(content.get("ratio")).isEqualTo(3.14);
            assertThat(content.get("label")).isEqualTo("hello");
        }

        @Test
        void quotedStringsRemainStrings() {
            String yaml = """
                          value: "true"
                          number: "42"
                          """;
            var result  = PlaybookParser.parseWithLocations(yaml, null);
            var content = result.document().content();
            assertThat(content.get("value")).isEqualTo("true");
            assertThat(content.get("number")).isEqualTo("42");
        }

        @Test
        void nullFilePath_producesNullFileInLocation() {
            String yaml = """
                          name: test
                          ---
                          - log: hello
                          """;
            var result = PlaybookParser.parseWithLocations(yaml, null);
            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) result.document().content().get("steps");
            SourceLocation loc = result.locations().locationOf(steps.get(0));
            assertThat(loc.file()).isNull();
            assertThat(loc.line()).isGreaterThan(0);
        }

        @Test
        void resolvesYaml11BooleanScalars() {
            String yaml = """
                          a: yes
                          b: no
                          c: on
                          d: off
                          e: "yes"
                          """;
            var result = PlaybookParser.parseWithLocations(yaml, null);
            var content = result.document().content();
            assertThat(content.get("a")).isEqualTo(true);
            assertThat(content.get("b")).isEqualTo(false);
            assertThat(content.get("c")).isEqualTo(true);
            assertThat(content.get("d")).isEqualTo(false);
            assertThat(content.get("e")).isEqualTo("yes");
        }

        @Test
        void resolvesNullVariants() {
            String yaml = """
                          a: null
                          b: ~
                          c:
                          """;
            var result = PlaybookParser.parseWithLocations(yaml, null);
            var content = result.document().content();
            assertThat(content.get("a")).isNull();
            assertThat(content.get("b")).isNull();
            assertThat(content.get("c")).isNull();
        }

    }
}
