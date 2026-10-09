package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.module.YamlModuleFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceDeclarationParseTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .registerModule(new YamlCoreJacksonModule());
    }

    @Test
    void parsesResourcesSection() throws Exception {
        String yaml = """
                resources:
                  minerals:
                    concurrency: 1
                  gas:
                    concurrency: 2
                steps:
                  step1:
                    action: test
                """;
        var file = mapper.readValue(yaml, YamlModuleFile.class);
        assertThat(file.resources()).hasSize(2);
        assertThat(file.resources().get("minerals").concurrency()).isEqualTo(1);
        assertThat(file.resources().get("gas").concurrency()).isEqualTo(2);
    }

    @Test
    void emptyResourcesIsEmptyMap() throws Exception {
        String yaml = """
                steps:
                  step1:
                    action: test
                """;
        var file = mapper.readValue(yaml, YamlModuleFile.class);
        assertThat(file.resources()).isNotNull().isEmpty();
    }

    @Test
    void resourcesSectionDoesNotAppearInSections() throws Exception {
        String yaml = """
                resources:
                  minerals:
                    concurrency: 1
                steps:
                  step1:
                    action: test
                """;
        var file = mapper.readValue(yaml, YamlModuleFile.class);
        assertThat(file.sections()).doesNotContainKey("resources");
    }
}
