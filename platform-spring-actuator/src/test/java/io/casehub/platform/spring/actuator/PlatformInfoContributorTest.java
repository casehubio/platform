package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformInfoContributorTest {

    @Test
    void contributesBackendsAndModelCount() {
        ModelRegistry registry = new ModelRegistry() {
            @Override public Optional<ModelDescriptor> resolveById(String id) { return Optional.empty(); }
            @Override public List<ModelDescriptor> query(ModelQuery q) { return List.of(); }
            @Override public List<ModelDescriptor> all() {
                return List.of(new ModelDescriptor("m1", "m1", "claude", null, "anthropic",
                        "claude", "Claude Sonnet", ModelTier.FLAGSHIP, Set.of(), 200000, 8192,
                        ModelLocality.CLOUD, null, null, null));
            }
        };
        AgentBackend backend = new AgentBackend() {
            @Override public String key() { return "claude"; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit i) { return null; }
        };

        var contributor = new PlatformInfoContributor(registry, List.of(backend));
        var builder = new Info.Builder();
        contributor.contribute(builder);
        var info = builder.build();

        @SuppressWarnings("unchecked")
        var platform = (Map<String, Object>) info.getDetails().get("casehub.platform");
        assertThat(platform).containsEntry("model.registry.size", 1);
        assertThat(platform).containsKey("agent.backends");
    }

    @Test
    void handlesNullDependencies() {
        var contributor = new PlatformInfoContributor(null, null);
        var builder = new Info.Builder();
        contributor.contribute(builder);
        var info = builder.build();

        assertThat(info.getDetails()).containsKey("casehub.platform");
    }
}
