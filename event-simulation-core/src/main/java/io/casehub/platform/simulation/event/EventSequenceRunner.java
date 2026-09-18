package io.casehub.platform.simulation.event;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class EventSequenceRunner {

    private final Consumer<CloudEvent> eventSink;

    public EventSequenceRunner(final Consumer<CloudEvent> eventSink) {
        this.eventSink = eventSink;
    }

    public SequenceResult run(final TimedSequence<CloudEvent> sequence) throws InterruptedException {
        final List<EmittedEvent> emitted = new ArrayList<>();
        final List<EmissionFailure> failures = new ArrayList<>();

        for (int i = 0; i < sequence.size(); i++) {
            final TimedEntry<CloudEvent> entry = sequence.entries().get(i);

            if (!entry.delay().isZero()) {
                Thread.sleep(entry.delay());
            }

            try {
                final CloudEvent stamped = CloudEventBuilder.from(entry.event())
                        .withId(UUID.randomUUID().toString())
                        .withTime(OffsetDateTime.now())
                        .build();
                eventSink.accept(stamped);
                emitted.add(new EmittedEvent("sequence[" + i + "]", stamped));
            } catch (final Exception e) {
                failures.add(new EmissionFailure("sequence[" + i + "]", e));
            }
        }

        return new SequenceResult(emitted, failures);
    }
}
