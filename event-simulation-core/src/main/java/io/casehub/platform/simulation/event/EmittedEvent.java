package io.casehub.platform.simulation.event;

import io.cloudevents.CloudEvent;

public record EmittedEvent(String qualifiedName, CloudEvent event) {}
