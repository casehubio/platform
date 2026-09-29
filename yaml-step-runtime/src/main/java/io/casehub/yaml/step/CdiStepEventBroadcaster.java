package io.casehub.yaml.step;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.function.Consumer;

@ApplicationScoped
public class CdiStepEventBroadcaster implements Consumer<ActionExecutionEvent> {

    @Inject
    Event<ActionExecutionEvent> event;

    @Override
    public void accept(ActionExecutionEvent actionExecutionEvent) {
        event.fireAsync(actionExecutionEvent);
    }
}
