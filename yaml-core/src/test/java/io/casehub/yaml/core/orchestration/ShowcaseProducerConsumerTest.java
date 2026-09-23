package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Showcase: producer-consumer with channel and semaphore rate limiting.
 *
 * Equivalent YAML:
 * <pre>
 * - parallel:
 *     - step: producer
 *       loop:
 *         count: 20
 *       action: generate-event
 *       publish:
 *         channel: events
 *         close-on-complete: true
 *       semaphore:
 *         name: event-rate
 *         permits: 5
 *
 *     - step: consumer
 *       loop:
 *         until: ${channel.events.closed}
 *       subscribe:
 *         channel: events
 *       action: process-event
 * </pre>
 */
class ShowcaseProducerConsumerTest {

    @Test
    void producerConsumer_withRateLimitedChannel() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        OrcChannel<String> channel = scope.channel("events");
        var semaphore = scope.semaphore("event-rate", 5);
        var consumed = new ArrayList<String>();

        // Producer: generates 20 events, rate-limited to 5 concurrent
        Thread.ofVirtual().name("producer").start(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    semaphore.acquire();
                    try {
                        channel.send("event-" + i);
                    } finally {
                        semaphore.release();
                    }
                }
                channel.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer: processes events until channel is closed
        Thread.ofVirtual().name("consumer").start(() -> {
            try {
                while (true) {
                    String event = channel.receive(500, TimeUnit.MILLISECONDS);
                    if (event == null) break;
                    consumed.add(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Wait for completion
        Thread.sleep(1000);
        assertThat(consumed).hasSize(20);
        assertThat(consumed.get(0)).isEqualTo("event-0");
        assertThat(consumed.get(19)).isEqualTo("event-19");

        scope.close();
    }
}
