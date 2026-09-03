package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.module.YamlModuleFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlModuleFileBuilderTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .registerModule(new YamlCoreJacksonModule());
    }

    @Test
    void top_level_keys_become_sections() throws Exception {
        String yaml = """
                module:
                  name: monitoring
                nodes:
                  monitor:
                    type: http-poller
                rules:
                  alert:
                    when: "status != 200"
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().name()).isEqualTo("monitoring");
        assertThat(file.sections()).containsKeys("nodes", "rules");
        assertThat(file.sections().get("nodes")).containsKey("monitor");
        assertThat(file.sections().get("rules")).containsKey("alert");
    }

    @Test
    void module_and_imports_not_captured_as_sections() throws Exception {
        String yaml = """
                module:
                  name: test
                imports:
                  - module: other
                    as: o
                nodes:
                  a:
                    type: sensor
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.sections()).containsOnlyKeys("nodes");
        assertThat(file.sections()).doesNotContainKeys("module", "imports");
        assertThat(file.imports()).hasSize(1);
    }

    @Test
    void sections_key_treated_as_section_not_wrapper() throws Exception {
        String yaml = """
                module:
                  name: test
                sections:
                  mysection:
                    key: value
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.sections()).containsOnlyKeys("sections");
        assertThat(file.sections().get("sections")).containsKey("mysection");
    }

    @Test
    void empty_file_module_only() throws Exception {
        String yaml = """
                module:
                  name: empty
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().name()).isEqualTo("empty");
        assertThat(file.sections()).isEmpty();
        assertThat(file.imports()).isEmpty();
    }

    @Test
    void nested_map_values_captured() throws Exception {
        String yaml = """
                module:
                  name: deep
                nodes:
                  sensor:
                    config:
                      interval: 30
                      retries: 3
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.sections().get("nodes").get("sensor"))
                .isInstanceOf(Map.class);
    }

    @Test
    void non_map_top_level_values_ignored() throws Exception {
        String yaml = """
                module:
                  name: test
                version: "1.0"
                nodes:
                  a:
                    type: sensor
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.sections()).containsOnlyKeys("nodes");
    }

    @Test
    void extends_field_deserialized() throws Exception {
        String yaml = """
                      module:
                        name: child
                        extends: parent
                      nodes:
                        notifier:
                          type: slack
                      """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().extendsModule()).isEqualTo("parent");
        assertThat(file.module().name()).isEqualTo("child");
        assertThat(file.sections()).containsKey("nodes");
    }

    @Test
    void no_extends_field_null() throws Exception {
        String yaml = """
                      module:
                        name: standalone
                      nodes:
                        monitor:
                          type: poller
                      """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().extendsModule()).isNull();
    }

    @Test
    void extends_with_params_and_sections() throws Exception {
        String yaml = """
                      module:
                        name: monitoring-with-slack
                        extends: monitoring
                        parameters:
                          slack_channel:
                            type: string
                            required: true
                      nodes:
                        slack-notifier:
                          type: notifier
                      """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().extendsModule()).isEqualTo("monitoring");
        assertThat(file.module().parameters()).containsKey("slack_channel");
        assertThat(file.sections()).containsKey("nodes");
    }
}
