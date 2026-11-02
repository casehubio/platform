package io.casehub.yaml.test;

import io.cloudevents.CloudEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventCapture {

    private final List<CapturedEvent> events = new CopyOnWriteArrayList<>();

    public void addEvent(CloudEvent event) {
        events.add(CapturedEvent.from(event));
    }

    public List<CapturedEvent> ofType(String type) {
        return events.stream()
                .filter(e -> e.type().equals(type))
                .toList();
    }

    public CapturedEvent last(String type) {
        CapturedEvent found = null;
        for (var e : events) {
            if (e.type().equals(type)) found = e;
        }
        return found;
    }

    public int count(String type) {
        return (int) events.stream()
                .filter(e -> e.type().equals(type))
                .count();
    }

    public List<CapturedEvent> all() {
        return List.copyOf(events);
    }

    public int size() {
        return events.size();
    }

    public void clear() {
        events.clear();
    }
}
