package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.error.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocationTrackingModuleTest {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new LocationTrackingModule());

    @Test
    void tracksRootMapPosition() throws Exception {
        String yaml = """
                      name: hello
                      count: 42
                      """;
        var index = new LocationIndex();
        ObjectReader reader = mapper.readerFor(Object.class)
                .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                .withAttribute(LocationTrackingModule.FILE_PATH_KEY, "test.yaml");
        Object result = reader.readValue(yaml);

        assertThat(result).isInstanceOf(Map.class);
        SourceLocation loc = index.locationOf(result);
        assertThat(loc.file()).isEqualTo("test.yaml");
        assertThat(loc.line()).isEqualTo(1);
        assertThat(loc.column()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tracksNestedMapPositions() throws Exception {
        String yaml = """
                      outer:
                        inner:
                          key: value
                      """;
        var index = new LocationIndex();
        ObjectReader reader = mapper.readerFor(Object.class)
                .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                .withAttribute(LocationTrackingModule.FILE_PATH_KEY, "nested.yaml");
        var result = (Map<String, Object>) reader.readValue(yaml);

        assertThat(index.locationOf(result).line()).isEqualTo(1);
        var outer = (Map<String, Object>) result.get("outer");
        assertThat(index.locationOf(outer).line()).isEqualTo(2);
        var inner = (Map<String, Object>) outer.get("inner");
        assertThat(index.locationOf(inner).line()).isEqualTo(3);
    }

    @Test
    void noOpWhenAttributesAbsent() throws Exception {
        String yaml = """
                      name: hello
                      """;
        Object result = mapper.readValue(yaml, Object.class);
        assertThat(result).isInstanceOf(Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tracksListPositions() throws Exception {
        String yaml = """
                      steps:
                        - log: hello
                        - process:
                            command: run.sh
                      """;
        var index = new LocationIndex();
        ObjectReader reader = mapper.readerFor(Object.class)
                .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                .withAttribute(LocationTrackingModule.FILE_PATH_KEY, "list.yaml");
        var result = (Map<String, Object>) reader.readValue(yaml);

        var steps = (List<Map<String, Object>>) result.get("steps");
        SourceLocation listLoc = index.locationOf(steps);
        assertThat(listLoc.file()).isEqualTo("list.yaml");
        assertThat(listLoc.line()).isEqualTo(2);

        assertThat(index.locationOf(steps.get(0)).line()).isEqualTo(2);
        assertThat(index.locationOf(steps.get(1)).line()).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tracksPositionsInTypedDeserialization() throws Exception {
        var typedMapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new LocationTrackingModule())
                .registerModule(new YamlCoreJacksonModule())
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        String yaml = """
                      module:
                        name: test-mod
                      steps:
                        do-work:
                          - log: hello
                      """;
        var index = new LocationIndex();
        ObjectReader reader = typedMapper.readerFor(io.casehub.yaml.core.module.YamlModuleFile.class)
                .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                .withAttribute(LocationTrackingModule.FILE_PATH_KEY, "module.yaml");
        var moduleFile = (io.casehub.yaml.core.module.YamlModuleFile) reader.readValue(yaml);

        assertThat(moduleFile.sections()).containsKey("steps");
        var stepsSection = moduleFile.sections().get("steps");
        assertThat(index.locationOf(stepsSection).line()).isGreaterThan(0);
    }
}
