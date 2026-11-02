package io.casehub.yaml.step.plugin;

import io.casehub.platform.simulation.event.CloudEventFixtureBuilder;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EmitEventPlugin implements Action {

    public static Definition definition() {
        return Definition.of("emit-event")
                .description("Fires a CloudEvent into the system")
                .absorbSiblings(true)
                .execute(new EmitEventPlugin())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        EventSink eventSink;
        try {
            eventSink = services.lookup(EventSink.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("No EventSink registered — emit-event requires an event sink");
        }

        Object subject = params.get("_subject");
        Map<String, Object> eventMap;

        if (subject instanceof String type) {
            eventMap = new LinkedHashMap<>();
            eventMap.put("type", type);
            eventMap.put("source", params.getOrDefault("source", "/test"));
            Object data = params.get("data");
            if (data != null) {
                eventMap.put("data", data);
            }
        } else if (subject instanceof Map) {
            eventMap = new LinkedHashMap<>((Map<String, Object>) subject);
        } else {
            return Result.failed("emit-event requires a type string or event map");
        }

        try {
            var event = CloudEventFixtureBuilder.fromMap(eventMap);
            eventSink.emit(event);
            return Result.of(Map.of("type", event.getType(), "id", event.getId()));
        } catch (Exception e) {
            return Result.failed("Failed to construct CloudEvent: " + e.getMessage());
        }
    }
}
