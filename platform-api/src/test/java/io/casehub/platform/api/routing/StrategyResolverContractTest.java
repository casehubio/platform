package io.casehub.platform.api.routing;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StrategyResolverContractTest {

    @Test
    void interfaceCompiles() {
        StrategyResolver resolver = new StrategyResolver() {
            @Override public <T extends NamedStrategy> T resolve(Class<T> type, String id) { return null; }
            @Override public <T extends NamedStrategy> Optional<T> find(Class<T> type, String id) { return Optional.empty(); }
            @Override public <T extends NamedStrategy> T defaultStrategy(Class<T> type) { return null; }
            @Override public <T extends NamedStrategy> List<T> available(Class<T> type) { return List.of(); }
        };
        assertThat(resolver).isNotNull();
    }
}
