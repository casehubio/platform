package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.TemporalEventSink;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.orchestration.ExecutionScope;

public record FeedBinding(String profileName, String channelName) {

    @SuppressWarnings("unchecked")
    public <E> TemporalEventSink<E> activate(ExecutionScope scope) {
        OrcChannel<E> channel = scope.channel(channelName);
        return (qualifiedName, label, event) -> {
            try {
                channel.send(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
