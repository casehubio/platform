package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepDefinitionTest {

    @Test
    void qualifiedNameWithNamespace() {
        var def = new StepDefinition("assess-risk", "Risk assessment", null, null,
                new InvokeBinding.Mcp("fsi.risk.assess"));
        assertThat(def.qualifiedName("fsitrading")).isEqualTo("fsitrading.assess-risk");
    }

    @Test
    void qualifiedNameWithoutNamespace() {
        var def = new StepDefinition("assess-risk", "Risk assessment", null, null,
                new InvokeBinding.Mcp("fsi.risk.assess"));
        assertThat(def.qualifiedName("")).isEqualTo("assess-risk");
    }

    @Test
    void inputsAndOutputsDefaultToEmptyMaps() {
        var def = new StepDefinition("test", null, null, null,
                new InvokeBinding.Mcp("test.tool"));
        assertThat(def.inputs()).isEmpty();
        assertThat(def.outputs()).isEmpty();
    }

    @Test
    void inputsAndOutputsArePreserved() {
        var inputs = Map.of("name", new StepParameter(StepParameterType.STRING, true, null, null, null, null));
        var outputs = Map.of("result", new StepParameter(StepParameterType.STRING, false, null, null, null, null));
        var def = new StepDefinition("test", "A test step", inputs, outputs,
                new InvokeBinding.Mcp("test.tool"));
        assertThat(def.inputs()).containsKey("name");
        assertThat(def.outputs()).containsKey("result");
        assertThat(def.description()).isEqualTo("A test step");
    }

    @Test
    void stepDefinitionFileWrapsActions() {
        var def = new StepDefinition("test", null, null, null,
                new InvokeBinding.Mcp("test.tool"));
        var file = new StepDefinitionFile("fsi", Map.of("test", def));
        assertThat(file.namespace()).isEqualTo("fsi");
        assertThat(file.actions()).containsKey("test");
    }

    @Test
    void stepDefinitionFileDefaultsNamespaceToEmpty() {
        var file = new StepDefinitionFile(null, Map.of());
        assertThat(file.namespace()).isEmpty();
    }

    @Test
    void stepDefinitionFileActionsAreImmutable() {
        var def = new StepDefinition("test", null, null, null,
                new InvokeBinding.Mcp("test.tool"));
        var mutable = new java.util.HashMap<String, StepDefinition>();
        mutable.put("test", def);
        var file = new StepDefinitionFile("ns", mutable);
        mutable.put("extra", def);
        assertThat(file.actions()).hasSize(1);
    }
}
