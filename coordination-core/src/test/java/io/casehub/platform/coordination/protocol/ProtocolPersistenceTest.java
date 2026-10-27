package io.casehub.platform.coordination.protocol;

import io.casehub.platform.coordination.FileCoordinationStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void stateRestoredAfterEvictAndReEvaluate() {
        var store = new FileCoordinationStateStore(tempDir);
        var evaluator = new PlaybookProtocolEvaluator(store);
        evaluator.register("proto", twoStateProtocol());

        var key = new ProtocolInstanceKey("proto", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());
        evaluator.evaluate(key, "QUERY", Map.of());

        var before = evaluator.evaluate(key, "QUERY", Map.of());
        assertThat(before).hasSize(1);

        evaluator.evict(key);

        var after = evaluator.evaluate(key, "QUERY", Map.of());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).severity()).isEqualTo(AdvisorySeverity.WARNING);
    }

    @Test
    void freshInstanceWhenNoStoredState() {
        var store = new FileCoordinationStateStore(tempDir);
        var evaluator = new PlaybookProtocolEvaluator(store);
        evaluator.register("proto", twoStateProtocol());

        var key = new ProtocolInstanceKey("proto", "ch-new");
        var result = evaluator.evaluate(key, "QUERY", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void noOpTransitionDoesNotSave() {
        var store = new FileCoordinationStateStore(tempDir);
        var evaluator = new PlaybookProtocolEvaluator(store);
        evaluator.register("proto", twoStateProtocol());

        var key = new ProtocolInstanceKey("proto", "ch1");
        evaluator.evaluate(key, "UNKNOWN", Map.of());

        assertThat(store.load(key.storeKey())).isEmpty();
    }

    @Test
    void stateChangingTransitionSaves() {
        var store = new FileCoordinationStateStore(tempDir);
        var evaluator = new PlaybookProtocolEvaluator(store);
        evaluator.register("proto", twoStateProtocol());

        var key = new ProtocolInstanceKey("proto", "ch1");
        evaluator.evaluate(key, "QUERY", Map.of());

        assertThat(store.load(key.storeKey())).isPresent();
    }

    @Test
    void newEvaluatorInstanceRestoresFromStore() {
        var store = new FileCoordinationStateStore(tempDir);

        var eval1 = new PlaybookProtocolEvaluator(store);
        eval1.register("proto", twoStateProtocol());
        var key = new ProtocolInstanceKey("proto", "ch1");
        eval1.evaluate(key, "QUERY", Map.of());
        eval1.evaluate(key, "QUERY", Map.of());

        var eval2 = new PlaybookProtocolEvaluator(store);
        eval2.register("proto", twoStateProtocol());
        var result = eval2.evaluate(key, "QUERY", Map.of());
        assertThat(result).hasSize(1);
    }

    private static Map<String, Object> twoStateProtocol() {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("IDLE", List.of(Map.of("on", Map.of("QUERY", "AWAITING"))));
        states.put("AWAITING", List.of(
            Map.of("on", Map.of("RESPONSE", "IDLE", "QUERY", "WARN"))
        ));
        states.put("WARN", List.of(
            Map.of("advisory", Map.of("severity", "WARNING", "message", "overloaded", "action", "INVESTIGATE")),
            Map.of("on", Map.of("RESPONSE", "IDLE", "QUERY", "WARN"))
        ));
        return states;
    }
}
