package io.casehub.platform.agent.router;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingAgentProviderTest {

    static AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override
            public String key() { return key; }

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().item(new AgentEvent.TextDelta("from-" + key));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) { return null; }
        };
    }

    static AgentBackend capturingBackend(String key, AtomicReference<AgentSessionConfig> configCapture,
                                         AtomicReference<AgentSessionInit> initCapture) {
        return new AgentBackend() {
            @Override
            public String key() { return key; }

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                configCapture.set(config);
                return Multi.createFrom().item(new AgentEvent.TextDelta("from-" + key));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                initCapture.set(init);
                return null;
            }
        };
    }

    static InMemoryBackendInstanceRegistry backendRegistry(AgentBackend... backends) {
        var registry = new InMemoryBackendInstanceRegistry();
        for (var backend : backends) {
            registry.register(backend);
        }
        return registry;
    }


    static ModelRegistry emptyRegistry() {
        return new ModelRegistry() {
            @Override
            public Optional<ModelDescriptor> resolveById(String id) { return Optional.empty(); }

            @Override
            public List<ModelDescriptor> query(ModelQuery query) { return List.of(); }

            @Override
            public List<ModelDescriptor> all() { return List.of(); }
        };
    }

    static ModelRegistry registryWith(ModelDescriptor... descriptors) {
        Map<String, ModelDescriptor> map = new HashMap<>();
        for (var d : descriptors) map.put(d.id(), d);
        return new ModelRegistry() {
            @Override
            public Optional<ModelDescriptor> resolveById(String id) {
                return Optional.ofNullable(map.get(id));
            }

            @Override
            public List<ModelDescriptor> query(ModelQuery query) { return List.copyOf(map.values()); }

            @Override
            public List<ModelDescriptor> all() { return List.copyOf(map.values()); }
        };
    }

    static ModelDescriptor descriptor(String id, String backendKey) {
        return new ModelDescriptor(id, id, backendKey, null, "test-vendor", "test-family",
                "Test " + id, ModelTier.STANDARD, Set.of(), 128000, 16384,
                ModelLocality.CLOUD, null, null, Map.of());
    }

    static ModelDescriptor descriptorWithInstance(String id, String backendKey, String instanceId) {
        return new ModelDescriptor(id, id, backendKey, instanceId, "test-vendor", "test-family",
                                   "Test " + id, ModelTier.STANDARD, Set.of(), 128000, 16384,
                                   ModelLocality.CLOUD, null, null, Map.of());
    }

    static ModelDescriptor descriptorWithTier(String id, String apiModelId,
                                              String backendKey, ModelTier tier) {
        return new ModelDescriptor(id, apiModelId, backendKey, null, "test-vendor", "test-family",
                                   "Test " + id, tier, Set.of(), 128000, 16384,
                                   ModelLocality.CLOUD, null, null, Map.of());
    }

    static ModelRegistry tierAwareRegistry(ModelDescriptor... descriptors) {
        Map<String, ModelDescriptor> map = new HashMap<>();
        List<ModelDescriptor>        all = List.of(descriptors);
        for (var d : descriptors) {map.put(d.id(), d);}
        return new ModelRegistry() {
            @Override
            public Optional<ModelDescriptor> resolveById(String id) {
                return Optional.ofNullable(map.get(id));
            }

            @Override
            public List<ModelDescriptor> query(ModelQuery query) {
                return all.stream()
                          .filter(d -> query.tier() == null || d.tier() == query.tier())
                          .filter(d -> query.vendor() == null || d.vendor().equals(query.vendor()))
                          .filter(d -> query.family() == null || d.family().equals(query.family()))
                          .toList();
            }

            @Override
            public List<ModelDescriptor> all() {return all;}
        };
    }


    // --- Existing behavior (key-based routing) ---

    @Test
    void routesByModelKey() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude"), stubBackend("openai")), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user", "openai");
        var events = router.invoke(config).collect().asList().await().indefinitely();
        assertThat(events).hasSize(1);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("from-openai");
    }

    @Test
    void nullModelUsesDefault() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude"), stubBackend("openai")), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user");
        var events = router.invoke(config).collect().asList().await().indefinitely();
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("from-claude");
    }

    @Test
    void unknownKeyWithNoRegistryMatchThrows() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user", "mistral");
        assertThatThrownBy(() -> router.invoke(config))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("mistral");
    }

    @Test
    void noDefaultBackendThrowsOnNullModel() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("openai")), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user");
        assertThatThrownBy(() -> router.invoke(config))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("No default backend");
    }

    @Test
    void openSessionRoutesToCorrectBackend() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude"), stubBackend("openai")), "claude", emptyRegistry());
        var init = AgentSessionInit.of("sys", "openai");
        assertThat(router.openSession(init)).isNull();
    }

    @Test
    void emptyBackendsThrowsOnAnyCall() {
        var router = new RoutingAgentProvider(backendRegistry(), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user");
        assertThatThrownBy(() -> router.invoke(config))
                  .isInstanceOf(IllegalStateException.class);
    }

    // --- Registry-path resolution ---

    @Test
    void registryPathResolvesModelToBackend() {
        var registry = registryWith(descriptor("claude-sonnet-5", "claude"));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude"), stubBackend("openai")), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "claude-sonnet-5");
        var events = router.invoke(config).collect().asList().await().indefinitely();
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("from-claude");
    }

    @Test
    void registryPathRewritesModelInConfig() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture = new AtomicReference<AgentSessionInit>();
        var registry = registryWith(descriptor("claude-sonnet-5", "claude"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "claude-sonnet-5");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-sonnet-5");
    }

    @Test
    void registryPathRewritesModelInSessionInit() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture = new AtomicReference<AgentSessionInit>();
        var registry = registryWith(descriptor("gpt-4.1", "openai"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("openai", configCapture, initCapture)), "openai", registry);
        var init = AgentSessionInit.of("sys", "gpt-4.1");
        router.openSession(init);
        assertThat(initCapture.get().model()).isEqualTo("gpt-4.1");
    }

    @Test
    void keyBasedPathNullsModelInConfig() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture = new AtomicReference<AgentSessionInit>();
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user", "claude");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isNull();
    }

    @Test
    void registryPathMissingBackendThrows() {
        var registry = registryWith(descriptor("gemini-2.5-pro", "gemini"));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "gemini-2.5-pro");
        assertThatThrownBy(() -> router.invoke(config))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("gemini")
                  .hasMessageContaining("no backend with that key/instance is registered");
    }

    @Test
    void registryTakesPriorityOverKeyMatch() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture = new AtomicReference<AgentSessionInit>();
        var registry = registryWith(descriptor("claude", "claude"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "claude");
        router.invoke(config).collect().asList().await().indefinitely();
        // Registry path sets the model ID; key-based path would null it
        assertThat(configCapture.get().model()).isEqualTo("claude");
    }

    @Test
    void resolvesByBackendInstanceId() {
        var vertexBackend = stubBackend("claude");
        var reg           = new InMemoryBackendInstanceRegistry();
        reg.register(new InstanceWrapper("claude", "default", stubBackend("claude")));
        reg.register(new InstanceWrapper("claude", "vertex", vertexBackend));
        var modelReg = registryWith(descriptorWithInstance("claude-vertex-model", "claude", "vertex"));
        var router   = new RoutingAgentProvider(reg, "claude", modelReg);
        var config   = AgentSessionConfig.of("sys", "user", "claude-vertex-model");
        var events   = router.invoke(config).collect().asList().await().indefinitely();
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("from-claude");
    }

    @Test
    void nullInstanceIdFallsBackToDefault() {
        var reg      = backendRegistry(stubBackend("claude"));
        var modelReg = registryWith(descriptor("claude-sonnet-5", "claude"));
        var router   = new RoutingAgentProvider(reg, "claude", modelReg);
        var config   = AgentSessionConfig.of("sys", "user", "claude-sonnet-5");
        var events   = router.invoke(config).collect().asList().await().indefinitely();
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("from-claude");
    }

    @Test
    void missingInstanceIdThrows() {
        var reg      = backendRegistry(stubBackend("claude"));
        var modelReg = registryWith(descriptorWithInstance("claude-vertex-model", "claude", "vertex"));
        var router   = new RoutingAgentProvider(reg, "claude", modelReg);
        var config   = AgentSessionConfig.of("sys", "user", "claude-vertex-model");
        assertThatThrownBy(() -> router.invoke(config))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("claude")
                  .hasMessageContaining("vertex");
    }
// --- Tier-based resolution ---

    @Test
    void tierRef_resolvesToDefaultBackendModel() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP),
                descriptorWithTier("gpt-4.1", "gpt-4.1", "openai", ModelTier.STANDARD));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture),
                                stubBackend("openai")),
                "claude", registry);

        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void tierRef_prefersDefaultBackend() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("o3", "o3", "openai", ModelTier.FLAGSHIP),
                descriptorWithTier("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture),
                                stubBackend("openai")),
                "claude", registry);

        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void tierRef_fallsBackToNonDefaultBackend() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("o3", "o3", "openai", ModelTier.FLAGSHIP));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude"),
                                capturingBackend("openai", configCapture, initCapture)),
                "claude", registry);

        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("o3");
    }

    @Test
    void tierRef_emptyRegistry_throwsWithNoSourcesMessage() {
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", emptyRegistry());
        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        assertThatThrownBy(() -> router.invoke(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No model sources configured");
    }

    @Test
    void tierRef_noMatchingTier_throwsWithAvailableTiers() {
        var registry = tierAwareRegistry(
                descriptorWithTier("claude-haiku", "claude-haiku", "claude", ModelTier.FAST));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        assertThatThrownBy(() -> router.invoke(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLAGSHIP")
                .hasMessageContaining("FAST");
    }

    @Test
    void tierRef_missingBackend_throwsIllegalState() {
        var registry = tierAwareRegistry(
                descriptorWithTier("gemini-pro", "gemini-pro", "gemini", ModelTier.FLAGSHIP));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        assertThatThrownBy(() -> router.invoke(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini");
    }

    @Test
    void tierRef_openSession_resolvesToCorrectModel() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("claude-haiku", "claude-haiku", "claude", ModelTier.FAST));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry);

        var init = AgentSessionInit.of("sys", "tier:FAST");
        router.openSession(init);
        assertThat(initCapture.get().model()).isEqualTo("claude-haiku");
    }

    @Test
    void tierRef_checkedBeforeRegistryId() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("tier:FLAGSHIP", "literal-id", "claude", ModelTier.STANDARD),
                descriptorWithTier("real-flagship", "real-flagship", "claude", ModelTier.FLAGSHIP));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry);

        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("real-flagship");
    }

    @Test
    void tierRef_invalidTierName_throwsIllegalArgument() {
        var registry = tierAwareRegistry(
                descriptorWithTier("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP));
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("claude")), "claude", registry);
        var config = AgentSessionConfig.of("sys", "user", "tier:INVALID");
        assertThatThrownBy(() -> router.invoke(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tierRef_existingResolutionPaths_unchanged() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithTier("claude-sonnet-5", "claude-sonnet-5", "claude", ModelTier.STANDARD));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry);

        var config = AgentSessionConfig.of("sys", "user", "claude-sonnet-5");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-sonnet-5");
    }

    static ModelDescriptor descriptorWithVendor(String id, String apiModelId, String backendKey, ModelTier tier, String vendor) {
        return new ModelDescriptor(id, apiModelId, backendKey, null, vendor, "test-family",
                                   "Test " + id, tier, Set.of("text", "reasoning"), 200000, 32768,
                                   ModelLocality.CLOUD, null, null, Map.of());
    }

    // --- Alias resolution ---

    @Test
    void aliasResolvesToMatchingModel() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"));
        var aliases = Map.of("reasoning-heavy",
                             ModelQuery.builder().tier(ModelTier.FLAGSHIP).requiredCapabilities(Set.of("reasoning")).build());
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, aliases);

        var config = AgentSessionConfig.of("sys", "user", "reasoning-heavy");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void aliasResolvesBeforeTierRef() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"),
                descriptorWithVendor("claude-haiku", "claude-haiku", "claude", ModelTier.FAST, "anthropic"));
        var aliases = Map.of("tier:FLAGSHIP",
                             ModelQuery.builder().tier(ModelTier.FAST).build());
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, aliases);

        var config = AgentSessionConfig.of("sys", "user", "tier:FLAGSHIP");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-haiku");
    }

    @Test
    void unknownAliasFallsToTierRef() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-haiku", "claude-haiku", "claude", ModelTier.FAST, "anthropic"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, Map.of());

        var config = AgentSessionConfig.of("sys", "user", "tier:FAST");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-haiku");
    }

    @Test
    void preferVendorTiebreaksAmongMatches() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"),
                descriptorWithVendor("o3", "o3", "openai", ModelTier.FLAGSHIP, "openai"));
        var aliases = Map.of("prefer-openai",
                             ModelQuery.builder().tier(ModelTier.FLAGSHIP).preferVendor("openai").build());
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture), stubBackend("openai")),
                "claude", registry, aliases);

        var config = AgentSessionConfig.of("sys", "user", "prefer-openai");
        router.invoke(config).collect().asList().await().indefinitely();
        // default backend is claude, but preferVendor=openai overrides for tiebreaking
        // However, default backend takes priority over preferVendor
        // Both are FLAGSHIP, claude is default backend → claude wins
        assertThat(configCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void preferVendorWinsWhenNoDefaultBackendMatch() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("o3", "o3", "openai", ModelTier.FLAGSHIP, "openai"),
                descriptorWithVendor("gemini-ultra", "gemini-ultra", "gemini", ModelTier.FLAGSHIP, "google"));
        var aliases = Map.of("prefer-google",
                             ModelQuery.builder().tier(ModelTier.FLAGSHIP).preferVendor("google").build());
        var router = new RoutingAgentProvider(
                backendRegistry(stubBackend("openai"),
                                capturingBackend("gemini", configCapture, initCapture)),
                "claude", registry, aliases);

        var config = AgentSessionConfig.of("sys", "user", "prefer-google");
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("gemini-ultra");
    }

    @Test
    void withModelCreatesNewConfig() {
        var config    = AgentSessionConfig.of("sys", "user");
        var withModel = config.withModel("tier:FAST");
        assertThat(withModel.model()).isEqualTo("tier:FAST");
        assertThat(withModel.systemPrompt()).isEqualTo("sys");
        assertThat(withModel.userPrompt()).isEqualTo("user");
        assertThat(config.model()).isNull();
    }

    @Test
    void directModelQueryDispatch() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, Map.of());

        var query  = ModelQuery.builder().tier(ModelTier.FLAGSHIP).build();
        var config = AgentSessionConfig.of("sys", "user").withModel(query);
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void directModelQueryOnSessionInit() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, Map.of());

        var query = ModelQuery.builder().tier(ModelTier.FLAGSHIP).build();
        var init  = AgentSessionInit.of("sys").withModel(query);
        router.openSession(init);
        assertThat(initCapture.get().model()).isEqualTo("claude-opus-5");
    }

    @Test
    void modelQueryTakesPrecedenceOverString() {
        var configCapture = new AtomicReference<AgentSessionConfig>();
        var initCapture   = new AtomicReference<AgentSessionInit>();
        var registry = tierAwareRegistry(
                descriptorWithVendor("claude-opus-5", "claude-opus-5", "claude", ModelTier.FLAGSHIP, "anthropic"),
                descriptorWithVendor("claude-haiku", "claude-haiku", "claude", ModelTier.FAST, "anthropic"));
        var router = new RoutingAgentProvider(
                backendRegistry(capturingBackend("claude", configCapture, initCapture)),
                "claude", registry, Map.of());

        var query  = ModelQuery.builder().tier(ModelTier.FAST).build();
        var config = new AgentSessionConfig("sys", "user", List.of(), null, null, "claude-opus-5", query);
        router.invoke(config).collect().asList().await().indefinitely();
        assertThat(configCapture.get().model()).isEqualTo("claude-haiku");
    }

    @Test
    void withModelQuerySetsQueryAndNullsString() {
        var config    = AgentSessionConfig.of("sys", "user", "claude-opus-5");
        var query     = ModelQuery.builder().tier(ModelTier.FAST).build();
        var withQuery = config.withModel(query);
        assertThat(withQuery.model()).isNull();
        assertThat(withQuery.modelQuery()).isEqualTo(query);
        assertThat(config.model()).isEqualTo("claude-opus-5");
    }


}
