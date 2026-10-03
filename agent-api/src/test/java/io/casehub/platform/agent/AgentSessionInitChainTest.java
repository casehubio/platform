package io.casehub.platform.agent;

import io.casehub.platform.api.model.ModelChain;
import io.casehub.platform.api.model.ModelQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionInitChainTest {

    @Test
    void existingConstructor_modelChainIsNull() {
        var init = AgentSessionInit.of("prompt");
        assertThat(init.modelChain()).isNull();
    }

    @Test
    void withModelChain_setsChainAndNullsModelAndQuery() {
        var init = AgentSessionInit.of("prompt", "opus");
        var chain = ModelChain.of("opus", "sonnet");
        var chained = init.withModelChain(chain);
        assertThat(chained.modelChain()).isEqualTo(chain);
        assertThat(chained.model()).isNull();
        assertThat(chained.modelQuery()).isNull();
    }

    @Test
    void withModel_string_nullsModelChain() {
        var chain = ModelChain.of("opus", "sonnet");
        var init = new AgentSessionInit("prompt", List.of(), null, null, null, null, chain);
        var rewritten = init.withModel("opus");
        assertThat(rewritten.model()).isEqualTo("opus");
        assertThat(rewritten.modelChain()).isNull();
        assertThat(rewritten.modelQuery()).isNull();
    }

    @Test
    void withModel_query_nullsModelChain() {
        var chain = ModelChain.of("opus", "sonnet");
        var init = new AgentSessionInit("prompt", List.of(), null, null, null, null, chain);
        var query = ModelQuery.builder().build();
        var rewritten = init.withModel(query);
        assertThat(rewritten.modelQuery()).isEqualTo(query);
        assertThat(rewritten.model()).isNull();
        assertThat(rewritten.modelChain()).isNull();
    }

    @Test
    void config_existingConstructor_modelChainIsNull() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.modelChain()).isNull();
    }

    @Test
    void config_withModelChain_setsChainAndNullsModelAndQuery() {
        var config = AgentSessionConfig.of("sys", "user", "opus");
        var chain = ModelChain.of("opus", "sonnet");
        var chained = config.withModelChain(chain);
        assertThat(chained.modelChain()).isEqualTo(chain);
        assertThat(chained.model()).isNull();
        assertThat(chained.modelQuery()).isNull();
    }

    @Test
    void config_withModel_string_nullsModelChain() {
        var chain = ModelChain.of("opus", "sonnet");
        var config = new AgentSessionConfig("sys", "user", List.of(), null, null, null, null, chain);
        var rewritten = config.withModel("opus");
        assertThat(rewritten.model()).isEqualTo("opus");
        assertThat(rewritten.modelChain()).isNull();
    }
}
