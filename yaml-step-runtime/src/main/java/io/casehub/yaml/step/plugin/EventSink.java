package io.casehub.yaml.step.plugin;

import io.cloudevents.CloudEvent;

@FunctionalInterface
public interface EventSink {
    void emit(CloudEvent event);
}
