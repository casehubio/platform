package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.casehub.platform.compiler.CompilationOutcome;
import io.casehub.platform.compiler.PlaybookCompiler;
import io.casehub.yaml.core.step.DeclarationParser;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;
import io.casehub.yaml.step.statemachine.PlaybookParser;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExampleExecutionTest {

    private static final Path EXAMPLES_DIR = resolveExamplesDir();

    private static Path resolveExamplesDir() {
        Path candidate = Path.of("examples/agent-interaction-patterns");
        if (Files.isDirectory(candidate)) return candidate;
        candidate = Path.of("../examples/agent-interaction-patterns");
        if (Files.isDirectory(candidate)) return candidate;
        return Path.of("examples/agent-interaction-patterns");
    }

    // --- Example execution: verify data flows through simulated LLM ---

    @Test
    void ex01_directInvoke_classifiesDocument() throws IOException {
        var env = buildFromExample("01-direct-invoke.yaml");
        var results = env.executeAll();

        assertThat(results).hasSize(3).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));
        assertThat(env.queriesSent()).hasSize(1);
        assertThat(env.queriesSent().getFirst()).contains("Classify this document");

        var queryResult = (Result.Success) results.get(1);
        assertThat(queryResult.output().get("response").toString())
                .as("Simulated LLM returns INVOICE for payment document")
                .contains("INVOICE");

        assertThat(env.pipeline.sessionCount()).as("Session released").isZero();
        env.shutdown();
    }

    @Test
    void ex02_conversation_multiTurnResponsesReflectPrompts() throws IOException {
        var env = buildFromExample("02-session-conversation.yaml");
        var results = env.executeAll();

        assertThat(results).hasSize(5).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        var r1 = ((Result.Success) results.get(1)).output().get("response").toString();
        var r2 = ((Result.Success) results.get(2)).output().get("response").toString();
        var r3 = ((Result.Success) results.get(3)).output().get("response").toString();
        assertThat(r1).as("First response addresses risk").contains("equities");
        assertThat(r2).as("Second response addresses rebalancing").contains("bonds");
        assertThat(r3).as("Third response gives bullet points").contains("1.");

        assertThat(env.pipeline.sessionCount()).isZero();
        env.shutdown();
    }

    @Test
    void ex03_clear_secondCustomerGetsCleanContext() throws IOException {
        var env = buildFromExample("03-session-with-clear.yaml");
        var results = env.executeAll();
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        assertThat(env.clearCount()).as("Context cleared between customers").isPositive();

        var responses = env.responsesCollected();
        assertThat(responses.get(0)).as("First customer: shipping issue").contains("shipping");
        assertThat(responses.get(1)).as("First customer: expedite").contains("expedited");

        int clearPos = env.eventLog.indexOf("clear");
        var postClearResponses = env.eventLog.stream().skip(clearPos + 1)
                .filter(e -> e.startsWith("response:")).toList();
        assertThat(postClearResponses.getFirst())
                .as("After clear: second customer gets billing response")
                .contains("duplicate charge");

        assertThat(env.pipeline.sessionCount()).isZero();
        env.shutdown();
    }

    @Test
    void ex05_poolBacked_infrastructureFromInlineDeclaration() throws IOException {
        var env = buildFromExample("05-pool-backed.yaml");

        assertThat(env.poolDeclarations).hasSize(1);
        var pool = env.poolDeclarations.getFirst();
        assertThat(pool.name()).isEqualTo("analysts");
        assertThat(pool.maxActive()).as("Pool max-active from YAML").isEqualTo(5);
        assertThat(pool.clearing()).as("Clearing config from YAML").containsEntry("policy", "size-aware");
        assertThat(pool.clearing()).containsEntry("threshold", 8000);

        var results = env.executeAll();
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        var r1 = ((Result.Success) results.get(1)).output().get("response").toString();
        var r2 = ((Result.Success) results.get(2)).output().get("response").toString();
        assertThat(r1).as("Response uses financial corpus").contains("earnings");
        assertThat(r2).as("Follow-up uses financial corpus").contains("AAPL");

        env.shutdown();
    }

    @Test
    void ex06_multiChannel_separatePoolsYieldIsolatedSessions() throws IOException {
        var env = buildFromExample("06-multi-channel.yaml");

        assertThat(env.poolDeclarations).hasSize(2);
        var voicePool = env.poolDeclarations.stream().filter(p -> "voice".equals(p.name())).findFirst().orElseThrow();
        var emailPool = env.poolDeclarations.stream().filter(p -> "email".equals(p.name())).findFirst().orElseThrow();
        assertThat(voicePool.maxActive()).as("Voice pool has higher capacity").isEqualTo(10);
        assertThat(emailPool.maxActive()).as("Email pool has lower capacity").isEqualTo(5);

        var results = env.executeAll();
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        var responses = env.responsesCollected();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0)).as("Voice response").contains("address");
        assertThat(responses.get(1)).as("Email response").contains("connection pool");

        env.shutdown();
    }

    @Test
    void ex07_salience_poolsHaveCorrectSalienceLevels() throws IOException {
        var env = buildFromExample("07-salience-priority.yaml");

        assertThat(env.poolDeclarations).hasSize(3);
        var byName = new LinkedHashMap<String, PoolDeclaration>();
        env.poolDeclarations.forEach(p -> byName.put(p.name(), p));

        assertThat(byName.get("voice").salience()).as("Voice: highest salience").isEqualTo(2);
        assertThat(byName.get("chat").salience()).as("Chat: medium salience").isEqualTo(1);
        assertThat(byName.get("email").salience()).as("Email: lowest salience").isZero();

        var results = env.executeAll();
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        var responses = env.responsesCollected();
        assertThat(responses).hasSize(3);
        assertThat(responses.get(0)).as("Voice: cancellation processed").contains("Cancellation");
        assertThat(responses.get(1)).as("Chat: export instructions").contains("Settings");
        assertThat(responses.get(2)).as("Email: pricing quote").contains("$45");

        env.shutdown();
    }

    @Test
    void ex08_structuredOutput_returnsCorpusJson() throws IOException {
        var env = buildFromExample("08-structured-output.yaml");
        var results = env.executeAll();
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(Result.Success.class));

        var r1 = ((Result.Success) results.get(1)).output().get("response").toString();
        var r2 = ((Result.Success) results.get(2)).output().get("response").toString();
        assertThat(r1).as("First extraction returns JSON with vendor").contains("ACME Corp");
        assertThat(r2).as("Corrected extraction returns new tax rate").contains("9%");

        env.shutdown();
    }

    // --- Behavioral property tests: pool mechanics ---

    @Test
    void pool_clearingEveryCall_clearsBeforeSecondAndSubsequentQueries() {
        var env = buildWithPool(new PoolDeclaration(
                "voice", "agent", "stub", 0, 5, null, Map.of(), Map.of(),
                0, Map.of("policy", "every-call"), 0));

        env.openSessionWithPolicy("call-1", "Voice support", ClearingPolicy.EVERY_CALL);
        env.querySession("call-1", "First question");
        assertThat(env.clearCount()).as("No clear before first query").isZero();

        env.querySession("call-1", "Second question");
        assertThat(env.clearCount()).as("Clear triggered before second query").isEqualTo(1);

        env.querySession("call-1", "Third question");
        assertThat(env.clearCount()).as("Clear triggered before third query").isEqualTo(2);

        env.closeSession("call-1");
        env.shutdown();
    }

    @Test
    void pool_clearingSizeAware_clearsWhenTokenThresholdExceeded() {
        int tokenThreshold = 3;
        var env = buildWithPoolAndThreshold(new PoolDeclaration(
                "email", "agent", "stub", 0, 5, null, Map.of(), Map.of(),
                0, Map.of("policy", "size-aware", "threshold", tokenThreshold), 0),
                tokenThreshold);

        env.openSessionWithPolicy("ticket-1", "Email agent", ClearingPolicy.SIZE_AWARE);

        env.querySession("ticket-1", "first query with some content");
        assertThat(env.clearCount()).as("First query: no accumulated tokens yet, no clear").isZero();

        env.querySession("ticket-1", "second query after tokens accumulated past threshold");
        assertThat(env.clearCount())
                .as("Second query: accumulated tokens from first response exceed threshold, triggers clear")
                .isPositive();

        env.closeSession("ticket-1");
        env.shutdown();
    }

    @Test
    void pool_sessionIsolation_concurrentSessionsGetOwnResponses() throws Exception {
        var env = buildWithPool(new PoolDeclaration(
                "multi", "agent", "stub", 0, 10, null, Map.of(), Map.of()));

        env.openSession("session-a", "Agent A");
        env.openSession("session-b", "Agent B");

        var latch = new CountDownLatch(2);
        var responseA = new java.util.concurrent.atomic.AtomicReference<String>();
        var responseB = new java.util.concurrent.atomic.AtomicReference<String>();

        Thread.ofVirtual().start(() -> {
            env.pipeline.executeSingle("session-a", session -> {
                var text = new StringBuilder();
                session.query("key indicators").subscribe().asStream().forEach(e -> {
                    if (e instanceof AgentEvent.TextDelta td) text.append(td.text());
                });
                responseA.set(text.toString());
                return null;
            }, Duration.ofSeconds(5));
            latch.countDown();
        });

        Thread.ofVirtual().start(() -> {
            env.pipeline.executeSingle("session-b", session -> {
                var text = new StringBuilder();
                session.query("delivery address").subscribe().asStream().forEach(e -> {
                    if (e instanceof AgentEvent.TextDelta td) text.append(td.text());
                });
                responseB.set(text.toString());
                return null;
            }, Duration.ofSeconds(5));
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(responseA.get()).as("Session A gets its own corpus response").contains("earnings");
        assertThat(responseB.get()).as("Session B gets its own corpus response").contains("address");

        env.closeSession("session-a");
        env.closeSession("session-b");
        env.shutdown();
    }

    @Test
    void pool_maxActive_limitsSimultaneousAdmission() throws Exception {
        var env = buildWithPool(new PoolDeclaration(
                "limited", "agent", "stub", 0, 2, null, Map.of(), Map.of()));

        env.openSession("s1", "Agent 1");
        env.openSession("s2", "Agent 2");

        var admitted = new java.util.concurrent.atomic.AtomicInteger(0);
        var latch = new CountDownLatch(2);

        Runnable occupySlot = () -> {
            try {
                env.pipeline.executeSingle("s1", session -> {
                    admitted.incrementAndGet();
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    return null;
                }, Duration.ofSeconds(2));
            } catch (Exception ignored) {}
            latch.countDown();
        };

        Thread.ofVirtual().start(occupySlot);
        Thread.ofVirtual().start(occupySlot);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(admitted.get())
                .as("Both requests admitted sequentially within max-active window")
                .isEqualTo(2);

        env.closeSession("s1");
        env.closeSession("s2");
        env.shutdown();
    }

    // --- Test infrastructure ---

    private ExampleEnvironment buildFromExample(String filename) throws IOException {
        String yaml = Files.readString(EXAMPLES_DIR.resolve(filename));
        var doc = PlaybookParser.parse(yaml);
        var outcome = new PlaybookCompiler().compile(doc);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);
        var poolDecls = ((CompilationOutcome.Success) outcome).result().poolDeclarations();
        var steps = extractSteps(yaml);

        if (poolDecls.isEmpty()) {
            poolDecls = List.of(new PoolDeclaration("default", "agent", "stub",
                    0, 20, null, Map.of(), Map.of()));
        } else {
            poolDecls = poolDecls.stream()
                    .map(p -> new PoolDeclaration(p.name(), p.agentId(), "stub",
                            p.minActive(), p.maxActive(), p.workingDir(),
                            p.scaling(), p.extensions(), p.salience(), p.clearing(), p.maxPromptTokens()))
                    .toList();
        }
        return new ExampleEnvironment(poolDecls, steps);
    }

    private ExampleEnvironment buildWithPool(PoolDeclaration decl) {
        return buildWithPoolAndThreshold(decl, 8000);
    }

    private ExampleEnvironment buildWithPoolAndThreshold(PoolDeclaration decl, int clearAtTokens) {
        var normalized = new PoolDeclaration(decl.name(), decl.agentId(), "stub",
                decl.minActive(), decl.maxActive(), decl.workingDir(),
                decl.scaling(), decl.extensions(), decl.salience(), decl.clearing(), decl.maxPromptTokens());
        return new ExampleEnvironment(List.of(normalized), List.of(), clearAtTokens);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSteps(String yaml) {
        var loader = new org.yaml.snakeyaml.Yaml();
        var docs = new ArrayList<Object>();
        for (Object doc : loader.loadAll(yaml)) docs.add(doc);
        Map<String, Object> content = docs.size() == 1
                ? (Map<String, Object>) docs.getFirst()
                : new LinkedHashMap<>((Map<String, Object>) docs.getFirst());
        if (docs.size() > 1) content.putAll((Map<String, Object>) docs.get(1));
        var steps = (List<Map<String, Object>>) content.get("steps");
        return steps != null ? steps : List.of();
    }

    static class ExampleEnvironment {
        final SessionPipeline pipeline;
        final SessionPoolRegistry poolRegistry;
        final List<InvokeHandler> handlers;
        final List<String> eventLog = new CopyOnWriteArrayList<>();
        final List<PoolDeclaration> poolDeclarations;
        private final List<Map<String, Object>> steps;

        ExampleEnvironment(List<PoolDeclaration> poolDecls, List<Map<String, Object>> steps) {
            this(poolDecls, steps, 8000);
        }

        ExampleEnvironment(List<PoolDeclaration> poolDecls, List<Map<String, Object>> steps, int clearAtTokens) {
            this.poolDeclarations = poolDecls;
            this.steps = steps;
            var backend = new CorpusBackend(eventLog);
            var backends = new AnyKeyBackendRegistry(backend);
            poolRegistry = new SessionPoolRegistry(poolDecls, backends);
            var manager = new SessionLifecycleManager(poolRegistry);
            pipeline = new SessionPipeline(manager, "stub", 20, clearAtTokens);
            handlers = List.of(
                    new AgentSessionOpenHandler(pipeline),
                    new AgentSessionQueryHandler(pipeline),
                    new AgentSessionClearHandler(pipeline),
                    new AgentSessionCloseHandler(pipeline));
        }

        List<Result> executeAll() {
            var results = new ArrayList<Result>();
            for (var step : steps) {
                if (step.containsKey("parallel")) continue;
                InvokeBinding binding = null;
                for (var key : step.keySet()) {
                    if (key.startsWith("agent-session-") || key.equals("agent-query")) {
                        binding = DeclarationParser.parseInvoke(step);
                        break;
                    }
                }
                if (binding == null) continue;
                InvokeBinding b = binding;
                var handler = handlers.stream().filter(h -> h.supports(b)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No handler for " + b));
                results.add(handler.create(null, b).execute(Map.of(), null));
            }
            return results;
        }

        void openSession(String id, String systemPrompt) {
            pipeline.openSession(id, AgentSessionInit.of(systemPrompt), ClearingPolicy.MANUAL);
        }

        void openSessionWithPolicy(String id, String systemPrompt, ClearingPolicy policy) {
            pipeline.openSession(id, AgentSessionInit.of(systemPrompt), policy);
        }


        void querySession(String id, String prompt) {
            pipeline.executeSingle(id, session -> {
                session.query(prompt).subscribe().asStream().forEach(e -> {});
                return null;
            }, Duration.ofSeconds(5));
        }

        void closeSession(String id) { pipeline.releaseSession(id); }

        List<String> queriesSent() {
            return eventLog.stream().filter(e -> e.startsWith("query:"))
                    .map(e -> e.substring("query:".length())).toList();
        }

        List<String> responsesCollected() {
            return eventLog.stream().filter(e -> e.startsWith("response:"))
                    .map(e -> e.substring("response:".length())).toList();
        }

        int clearCount() {
            return (int) eventLog.stream().filter("clear"::equals).count();
        }

        void shutdown() {
            pipeline.shutdown();
            poolRegistry.shutdown();
        }
    }

    private static class CorpusBackend implements AgentBackend {
        private final List<String> log;
        CorpusBackend(List<String> log) { this.log = log; }
        @Override public String key() { return "stub"; }

        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            log.add("invoke:" + config.userPrompt());
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(respond(config.userPrompt())),
                    invocationComplete(config.userPrompt()));
        }

        @Override public AgentSession openSession(AgentSessionInit init) {
            log.add("openSession:" + init.systemPrompt());
            return new CorpusSession(log);
        }

        static String respond(String prompt) {
            if (prompt == null) return "No prompt";
            if (prompt.contains("Classify")) return "INVOICE";
            if (prompt.contains("rebalancing")) return "Shift 10% equities to bonds for downturn protection.";
            if (prompt.contains("risk profile")) return "Moderate risk: 60% equities creates growth exposure.";
            if (prompt.contains("bullet points")) return "1. Reduce equity. 2. Increase bonds. 3. Hold cash.";
            if (prompt.contains("order #4521")) return "I see your order. Checking shipping status now.";
            if (prompt.contains("expedite")) return "I've requested expedited shipping for your replacement.";
            if (prompt.contains("charged twice")) return "Found the duplicate charge. Refund of $12.99 initiated.";
            if (prompt.contains("delivery address")) return "Sure, what's the new address?";
            if (prompt.contains("API timeouts")) return "Pattern suggests connection pool exhaustion between 14-16 UTC.";
            if (prompt.contains("cancel my subscription")) return "Cancellation processed effective today.";
            if (prompt.contains("export my data")) return "Go to Settings > Privacy > Export Data.";
            if (prompt.contains("enterprise pricing")) return "Enterprise 500-seat plan: $45/seat/month, annual billing.";
            if (prompt.contains("ACME Corp")) return "{\"vendor\":\"ACME Corp\",\"invoiceNumber\":\"INV-2024-0891\",\"total\":496.80}";
            if (prompt.contains("tax rate")) return "{\"total\":501.40,\"tax\":41.40,\"taxRate\":\"9%\"}";
            if (prompt.contains("key indicators")) return "Watch earnings growth, P/E ratios, and Fed rate policy.";
            if (prompt.contains("stocks")) return "Monitor AAPL, MSFT, NVDA for Q4 positioning.";
            return "Response: " + prompt.substring(0, Math.min(40, prompt.length()));
        }

        static AgentEvent.InvocationComplete invocationComplete(String prompt) {
            int inputTokens = prompt != null ? prompt.length() / 4 : 0;
            return new AgentEvent.InvocationComplete(inputTokens, 20, 0, 0, 0, 0.001, 50L, 0L, null, 0, false, null, null);
        }
    }

    private static class CorpusSession implements AgentSession {
        private final List<String> log;
        CorpusSession(List<String> log) { this.log = log; }
        @Override public Multi<AgentEvent> query(String prompt) {
            log.add("query:" + prompt);
            String response = CorpusBackend.respond(prompt);
            log.add("response:" + response);
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    CorpusBackend.invocationComplete(prompt));
        }
        @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
        @Override public void clear() { log.add("clear"); }
        @Override public void close(Duration maxWait) { log.add("close"); }
        @Override public void close() { close(Duration.ofSeconds(30)); }
    }

    private record AnyKeyBackendRegistry(AgentBackend backend) implements BackendInstanceRegistry {
        @Override public void register(AgentBackend b) {}
        @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
            return Optional.of(backend);
        }
        @Override public List<AgentBackend> resolveByKey(String key) { return List.of(backend); }
    }
}
