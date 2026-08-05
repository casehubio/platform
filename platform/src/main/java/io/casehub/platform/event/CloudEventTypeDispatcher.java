package io.casehub.platform.event;

import io.cloudevents.CloudEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CloudEventTypeDispatcher {

    private static final Logger LOG = Logger.getLogger(CloudEventTypeDispatcher.class);

    private final Event<CloudEvent> cloudEventBus;

    @Inject
    public CloudEventTypeDispatcher(Event<CloudEvent> cloudEventBus) {
        this.cloudEventBus = cloudEventBus;
    }

    public void onCloudEvent(@ObservesAsync CloudEvent event) {
        String type = event.getType();
        if (type == null || type.isBlank()) {
            return;
        }
        ArcContainer container = Arc.container();
        if (container != null && !container.isRunning()) {
            return;
        }
        cloudEventBus.select(new CloudEventTypeLiteral(type))
                .fireAsync(event)
                .exceptionally(ex -> {
                    LOG.errorf(ex, "Typed CloudEvent observer failed for type=%s, id=%s",
                               type, event.getId());
                    return event;
                });
    }
}
