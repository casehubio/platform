package io.casehub.yaml.step;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.function.Consumer;

@ApplicationScoped
public class CdiStepEventBroadcaster implements Consumer<StepExecutionEvent> {

    @Inject
    Event<StepExecutionEvent> event;

    @Override
    public void accept(StepExecutionEvent stepExecutionEvent) {
        event.fireAsync(stepExecutionEvent);
    }
}
