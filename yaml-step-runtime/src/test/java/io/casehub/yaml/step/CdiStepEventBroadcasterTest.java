package io.casehub.yaml.step;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CdiStepEventBroadcasterTest {

    @Test
    void acceptFiresAsyncEvent() throws Exception {
        var broadcaster = new CdiStepEventBroadcaster();
        AtomicReference<ActionExecutionEvent> fired = new AtomicReference<>();

        Field eventField = CdiStepEventBroadcaster.class.getDeclaredField("event");
        eventField.setAccessible(true);
        eventField.set(broadcaster, new StubEvent(fired));

        var event = new ActionExecutionEvent("test-action", 42, true, Map.of());
        broadcaster.accept(event);

        assertThat(fired.get()).isSameAs(event);
    }

    @SuppressWarnings("unchecked")
    private static class StubEvent implements Event<ActionExecutionEvent> {
        private final AtomicReference<ActionExecutionEvent> captured;

        StubEvent(AtomicReference<ActionExecutionEvent> captured) {
            this.captured = captured;
        }

        @Override
        public void fire(ActionExecutionEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends ActionExecutionEvent> CompletionStage<U> fireAsync(U event) {
            captured.set(event);
            return CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends ActionExecutionEvent> CompletionStage<U> fireAsync(U event,
                NotificationOptions options) {
            return fireAsync(event);
        }

        @Override
        public Event<ActionExecutionEvent> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends ActionExecutionEvent> Event<U> select(Class<U> subtype,
                Annotation... qualifiers) {
            return (Event<U>) this;
        }

        @Override
        public <U extends ActionExecutionEvent> Event<U> select(TypeLiteral<U> subtype,
                Annotation... qualifiers) {
            return (Event<U>) this;
        }
    }
}
