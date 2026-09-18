package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.simulation.CorpusSeed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCorpusTest {

    @Test
    void invokeReturnsPreConfiguredSeed() {
        CorpusSeed<AgentSimulationInput, List<AgentEvent>> seed = AgentCorpus.invoke("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("agent-provider.invoke");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void inputCreatesAgentSimulationInput() {
        AgentSimulationInput input = AgentCorpus.input("system", "user");
        assertThat(input.systemPrompt()).isEqualTo("system");
        assertThat(input.userPrompt()).isEqualTo("user");
        assertThat(input.model()).isNull();
    }

    @Test
    void textResponseCreatesTextDeltaList() {
        List<AgentEvent> events = AgentCorpus.textResponse("hello");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.TextDelta.class);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("hello");
    }

    @Test
    void qnConstantMatchesExpectedValue() {
        assertThat(AgentProviderQN.INVOKE).isEqualTo("agent-provider.invoke");
    }

    @Test
    void endToEndSeedAccumulation() {
        var seed = AgentCorpus.invoke("hospital-a");
        seed.add(AgentCorpus.input("Triage agent", "Patient has chest pain"),
                AgentCorpus.textResponse("Priority 1 — cardiac consult"));

        assertThat(seed.build()).hasSize(1);
        assertThat(seed.build().get(0).input().systemPrompt()).isEqualTo("Triage agent");
    }
}
