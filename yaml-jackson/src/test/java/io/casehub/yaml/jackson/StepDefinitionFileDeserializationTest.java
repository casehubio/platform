package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;
import io.casehub.yaml.core.step.StepParameterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepDefinitionFileDeserializationTest {

    private ObjectMapper yamlMapper;

    @BeforeEach
    void setUp() {
        yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.registerModule(new YamlCoreJacksonModule());
    }

    private static final String STEP_YAML = """
            namespace: fsi
            assess-risk:
              description: Evaluate risk
              inputs:
                instrumentId:
                  type: string
                  required: true
              outputs:
                level:
                  type: string
                  enum:
                    - LOW
                    - MEDIUM
                    - HIGH
              invoke:
                mcp: fsi.risk.assess
            notify:
              invoke:
                rest:
                  method: POST
                  url: /api/notifications
                  body:
                    message: "${message}"
            """;

    @Test
    void deserializesStepDefinitionFile() throws Exception {
        StepDefinitionFile file = yamlMapper.readValue(STEP_YAML, StepDefinitionFile.class);

        assertThat(file.namespace()).isEqualTo("fsi");
        assertThat(file.actions()).hasSize(2);

        StepDefinition assessRisk = file.actions().get("assess-risk");
        assertThat(assessRisk.name()).isEqualTo("assess-risk");
        assertThat(assessRisk.description()).isEqualTo("Evaluate risk");
        assertThat(assessRisk.inputs().get("instrumentId").type()).isEqualTo(StepParameterType.STRING);
        assertThat(assessRisk.inputs().get("instrumentId").required()).isTrue();
        assertThat(assessRisk.outputs().get("level").allowedValues()).containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(assessRisk.invoke()).isInstanceOf(InvokeBinding.Mcp.class);

        StepDefinition notify = file.actions().get("notify");
        assertThat(notify.invoke()).isInstanceOf(InvokeBinding.Rest.class);
        InvokeBinding.Rest rest = (InvokeBinding.Rest) notify.invoke();
        assertThat(rest.method()).isEqualTo("POST");
        assertThat(rest.url()).isEqualTo("/api/notifications");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jacksonAndParserProduceSameModel() throws Exception {
        StepDefinitionFile fromJackson = yamlMapper.readValue(STEP_YAML, StepDefinitionFile.class);

        Map<String, Object> rawMap = yamlMapper.readValue(STEP_YAML, Map.class);
        Map<String, Object> actionsMap = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            if (!"namespace".equals(entry.getKey())) {
                actionsMap.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, Object> parserInput = Map.of(
                "namespace", rawMap.getOrDefault("namespace", ""),
                "actions", actionsMap);
        StepDefinitionFile fromParser = StepDefinitionParser.parse(parserInput);

        assertThat(fromJackson.namespace()).isEqualTo(fromParser.namespace());
        assertThat(fromJackson.actions().keySet()).isEqualTo(fromParser.actions().keySet());

        for (String actionName : fromJackson.actions().keySet()) {
            StepDefinition jacksonDef = fromJackson.actions().get(actionName);
            StepDefinition parserDef = fromParser.actions().get(actionName);

            assertThat(jacksonDef.name()).isEqualTo(parserDef.name());
            assertThat(jacksonDef.description()).isEqualTo(parserDef.description());
            assertThat(jacksonDef.inputs().keySet()).isEqualTo(parserDef.inputs().keySet());
            assertThat(jacksonDef.outputs().keySet()).isEqualTo(parserDef.outputs().keySet());
            assertThat(jacksonDef.invoke().getClass()).isEqualTo(parserDef.invoke().getClass());
        }
    }

    @Test
    void deserializesAgentBinding() throws Exception {
        String yaml = """
                analyse:
                  invoke:
                    agent:
                      descriptor: trade-analyst
                      model: claude-sonnet-5
                      structured-output: true
                """;

        StepDefinitionFile file = yamlMapper.readValue(yaml, StepDefinitionFile.class);
        InvokeBinding.Agent agent = (InvokeBinding.Agent) file.actions().get("analyse").invoke();
        assertThat(agent.descriptor()).isEqualTo("trade-analyst");
        assertThat(agent.model()).isEqualTo("claude-sonnet-5");
        assertThat(agent.structuredOutput()).isTrue();
    }

    @Test
    void deserializesProcessBinding() throws Exception {
        String yaml = """
                calc:
                  invoke:
                    process:
                      command: /opt/calc
                      args:
                        - "--mode"
                        - "fast"
                      output: json
                      timeout: 30s
                      working-dir: /opt/build
                """;

        StepDefinitionFile file = yamlMapper.readValue(yaml, StepDefinitionFile.class);
        InvokeBinding.Process proc = (InvokeBinding.Process) file.actions().get("calc").invoke();
        assertThat(proc.command()).isEqualTo("/opt/calc");
        assertThat(proc.args()).containsExactly("--mode", "fast");
        assertThat(proc.output()).isEqualTo("json");
        assertThat(proc.workingDir()).isEqualTo("/opt/build");
    }

    @Test
    void defaultNamespaceIsEmpty() throws Exception {
        String yaml = """
                test:
                  invoke:
                    mcp: test.tool
                """;

        StepDefinitionFile file = yamlMapper.readValue(yaml, StepDefinitionFile.class);
        assertThat(file.namespace()).isEmpty();
    }
}
