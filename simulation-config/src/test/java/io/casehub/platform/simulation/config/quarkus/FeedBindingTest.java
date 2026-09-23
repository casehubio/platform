package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.TemporalEventSink;
import io.casehub.yaml.core.orchestration.DefaultScenarioScope;
import io.casehub.yaml.core.orchestration.OrcChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedBindingTest {

    @Test
    void activate_createsSinkThatSendsToChannel() throws InterruptedException {
        var scope   = new DefaultScenarioScope();
        var binding = new FeedBinding("flash-crash", "trades");

        TemporalEventSink<String> sink = binding.activate(scope);
        sink.deliver("trades", "event-1", "event-1-data");
        sink.deliver("trades", "event-2", "event-2-data");

        OrcChannel<String> channel = scope.channel("trades");
        assertThat(channel.receive()).isEqualTo("event-1-data");
        assertThat(channel.receive()).isEqualTo("event-2-data");
        scope.close();
    }

    @Test
    void activate_channelCreatedOnDemand() throws InterruptedException {
        var scope   = new DefaultScenarioScope();
        var binding = new FeedBinding("profile", "new-channel");

        TemporalEventSink<Integer> sink = binding.activate(scope);
        sink.deliver("qn", "label", 42);

        OrcChannel<Integer> channel = scope.channel("new-channel");
        assertThat(channel.receive()).isEqualTo(42);
        scope.close();
    }
}
