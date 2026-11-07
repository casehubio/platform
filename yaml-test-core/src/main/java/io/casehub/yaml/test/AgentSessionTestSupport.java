package io.casehub.yaml.test;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentSessionTestSupport implements AutoCloseable {

    private final SessionPipeline pipeline;
    private final SessionPoolRegistry poolRegistry;

    private AgentSessionTestSupport(SessionPipeline pipeline,
                                     SessionPoolRegistry poolRegistry) {
        this.pipeline = pipeline;
        this.poolRegistry = poolRegistry;
    }

    @SuppressWarnings("unchecked")
    public static AgentSessionTestSupport setup(
            Map<String, Object> simulateBlock,
            CompositePluginRegistry pluginRegistry,
            List<PoolDeclaration> compiledPools,
            io.casehub.platform.simulation.SimulationRuntime simulationRuntime) {

        Map<String, String> corpus            = extractCorpus(simulateBlock);
        int                 clearingThreshold = extractConfigInt(simulateBlock, "clearing-threshold", 8000);
        int                 maxActive         = extractConfigInt(simulateBlock, "max-active", 20);

        AgentBackend            backend  = new CorpusBackend(corpus, simulationRuntime);
        BackendInstanceRegistry backends = new SingleBackendRegistry(backend);

        List<PoolDeclaration> pools = compiledPools != null && !compiledPools.isEmpty()
                                      ? compiledPools.stream()
                                                     .map(p -> new PoolDeclaration(p.name(), p.agentId(), "stub",
                                                                                   p.minActive(), p.maxActive(), p.workingDir(),
                                                                                   p.scaling(), p.extensions(), p.salience(), p.clearing(),
                                                                                   p.maxPromptTokens()))
                                                     .toList()
                                      : List.of(new PoolDeclaration("default", "agent", "stub",
                                                                    0, maxActive, null, Map.of(), Map.of()));

        var poolRegistry = new SessionPoolRegistry(pools, backends);
        var manager      = new SessionLifecycleManager(poolRegistry);
        var pipeline     = new SessionPipeline(manager, "stub", maxActive, clearingThreshold);

        for (var def : AgentSessionPlugins.definitions(pipeline)) {
            pluginRegistry.register(def);
        }

        return new AgentSessionTestSupport(pipeline, poolRegistry);
    }

    @Override
    public void close() {
        pipeline.shutdown();
        poolRegistry.shutdown();
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> extractCorpus(Map<String, Object> simulateBlock) {
        Map<String, String> corpus = new LinkedHashMap<>();
        Object agentBlock = simulateBlock.get("agent");
        if (agentBlock instanceof List<?> list) {
            for (var entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    String prompt = String.valueOf(map.get("prompt"));
                    String response = String.valueOf(map.get("response"));
                    corpus.put(prompt, response);
                }
            }
        } else if (agentBlock instanceof Map<?, ?> map) {
            var configMap = (Map<String, Object>) map;
            if (configMap.containsKey("corpus")) {
                for (var entry : (List<Map<String, Object>>) configMap.get("corpus")) {
                    corpus.put(String.valueOf(entry.get("prompt")),
                               String.valueOf(entry.get("response")));
                }
            }
        }
        return corpus;
    }

    @SuppressWarnings("unchecked")
    private static int extractConfigInt(Map<String, Object> simulateBlock,
                                         String key, int defaultValue) {
        Object agentBlock = simulateBlock.get("agent");
        if (agentBlock instanceof Map<?, ?> map) {
            var configMap = (Map<String, Object>) map;
            if (configMap.containsKey("config")) {
                var config = (Map<String, Object>) configMap.get("config");
                if (config.containsKey(key)) {
                    return ((Number) config.get(key)).intValue();
                }
            }
        }
        return defaultValue;
    }

    static class CorpusBackend implements AgentBackend {
        private static final String                                           AGENT_QN = "agent-provider.invoke";
        private final        Map<String, String>                              corpus;
        private final        io.casehub.platform.simulation.SimulationRuntime simulationRuntime;

        CorpusBackend(Map<String, String> corpus, io.casehub.platform.simulation.SimulationRuntime simulationRuntime) {
            this.corpus            = corpus;
            this.simulationRuntime = simulationRuntime;
        }

        @Override
        public String key() {return "stub";}

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            String response = lookupResponse(config.userPrompt());
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 50L, 0L, null, 0, false, null, null));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return new CorpusSession(corpus, simulationRuntime);
        }

        String lookupResponse(String prompt) {
            if (simulationRuntime != null) {
                var strategy = simulationRuntime.<Object, Object>strategyFor(AGENT_QN);
                if (strategy.isPresent() && strategy.get().canResolve(prompt)) {
                    return String.valueOf(strategy.get().resolve(prompt));
                }
            }
            if (prompt == null) {return "No prompt";}
            return corpus.entrySet().stream()
                         .filter(e -> prompt.contains(e.getKey()))
                         .map(Map.Entry::getValue)
                         .findFirst()
                         .orElse("Response: " + prompt.substring(0, Math.min(40, prompt.length())));
        }
    }

    private static class CorpusSession implements AgentSession {
        private static final String                                           AGENT_QN = "agent-provider.invoke";
        private final        Map<String, String>                              corpus;
        private final        io.casehub.platform.simulation.SimulationRuntime simulationRuntime;

        CorpusSession(Map<String, String> corpus, io.casehub.platform.simulation.SimulationRuntime simulationRuntime) {
            this.corpus            = corpus;
            this.simulationRuntime = simulationRuntime;
        }

        @Override
        public Multi<AgentEvent> query(String prompt) {
            String response = lookupResponse(prompt);
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 50L, 0L, null, 0, false, null, null));
        }

        private String lookupResponse(String prompt) {
            if (simulationRuntime != null) {
                var strategy = simulationRuntime.<Object, Object>strategyFor(AGENT_QN);
                if (strategy.isPresent() && strategy.get().canResolve(prompt)) {
                    return String.valueOf(strategy.get().resolve(prompt));
                }
            }
            return corpus.entrySet().stream()
                         .filter(e -> prompt != null && prompt.contains(e.getKey()))
                         .map(Map.Entry::getValue)
                         .findFirst()
                         .orElse("Response: " + (prompt != null ? prompt.substring(0, Math.min(40, prompt.length())) : "null"));
        }

        @Override
        public Uni<Void> interrupt()        {return Uni.createFrom().voidItem();}

        @Override
        public void clear()                 {}

        @Override
        public void close(Duration maxWait) {}

        @Override
        public void close()                 {close(Duration.ofSeconds(30));}
    }

    private record SingleBackendRegistry(AgentBackend backend) implements BackendInstanceRegistry {
        @Override public void register(AgentBackend b) {}
        @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
            return Optional.of(backend);
        }
        @Override public List<AgentBackend> resolveByKey(String key) { return List.of(backend); }
    }
}
