package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.module.ParameterType;
import io.casehub.yaml.core.module.YamlModuleFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterTypeCaseInsensitiveTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build()
                .registerModule(new YamlCoreJacksonModule());
    }

    @Test
    void lowercase_type_accepted() throws Exception {
        String yaml = """
                module:
                  name: test
                  parameters:
                    region:
                      type: string
                      required: true
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().parameters().get("region").type())
                .isEqualTo(ParameterType.STRING);
    }

    @Test
    void mixed_case_accepted() throws Exception {
        String yaml = """
                module:
                  name: test
                  parameters:
                    count:
                      type: Integer
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().parameters().get("count").type())
                .isEqualTo(ParameterType.INTEGER);
    }

    @Test
    void uppercase_still_works() throws Exception {
        String yaml = """
                module:
                  name: test
                  parameters:
                    flag:
                      type: BOOLEAN
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().parameters().get("flag").type())
                .isEqualTo(ParameterType.BOOLEAN);
    }

    @Test
    void all_types_case_insensitive() throws Exception {
        String yaml = """
                module:
                  name: test
                  parameters:
                    a:
                      type: string
                    b:
                      type: list
                    c:
                      type: integer
                    d:
                      type: number
                    e:
                      type: boolean
                """;

        YamlModuleFile file = mapper.readValue(yaml, YamlModuleFile.class);

        assertThat(file.module().parameters().get("a").type()).isEqualTo(ParameterType.STRING);
        assertThat(file.module().parameters().get("b").type()).isEqualTo(ParameterType.LIST);
        assertThat(file.module().parameters().get("c").type()).isEqualTo(ParameterType.INTEGER);
        assertThat(file.module().parameters().get("d").type()).isEqualTo(ParameterType.NUMBER);
        assertThat(file.module().parameters().get("e").type()).isEqualTo(ParameterType.BOOLEAN);
    }
}
