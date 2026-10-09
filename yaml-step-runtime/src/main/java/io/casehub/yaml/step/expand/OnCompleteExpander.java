package io.casehub.yaml.step.expand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class OnCompleteExpander {

    private OnCompleteExpander() {}

    public record ExpandedSteps(List<Map<String, Object>> steps, SourceLocationMap locations) {}

    public static ExpandedSteps expand(List<Map<String, Object>> steps) {
        var counter = new AtomicInteger(0);
        var locations = new SourceLocationMap();
        var result = new ArrayList<Map<String, Object>>();
        expandRecursive(steps, result, counter, locations);
        return new ExpandedSteps(result, locations);
    }

    @SuppressWarnings("unchecked")
    private static void expandRecursive(List<Map<String, Object>> steps,
                                         List<Map<String, Object>> output,
                                         AtomicInteger counter,
                                         SourceLocationMap locations) {
        for (var step : steps) {
            Object onComplete = step.get("on-complete");
            if (onComplete == null) {
                output.add(step);
                continue;
            }

            var expanded = new LinkedHashMap<>(step);
            expanded.remove("on-complete");

            int id = counter.incrementAndGet();
            String name;
            if (expanded.containsKey("name")) {
                name = (String) expanded.get("name");
            } else {
                name = "__oc_" + id;
                expanded.put("name", name);
                locations.record(name, "on-complete block #" + id);
            }

            String signalName = name + "_done";
            Object existingSignal = expanded.get("signal");
            if (existingSignal != null) {
                var signals = new ArrayList<String>();
                if (existingSignal instanceof List<?> list) {
                    list.forEach(s -> signals.add((String) s));
                } else {
                    signals.add((String) existingSignal);
                }
                signals.add(signalName);
                expanded.put("signal", signals);
            } else {
                expanded.put("signal", signalName);
            }

            output.add(expanded);

            List<Map<String, Object>> continuations = (List<Map<String, Object>>) onComplete;
            var continuationSteps = new ArrayList<Map<String, Object>>();
            for (var cont : continuations) {
                var contExpanded = new LinkedHashMap<>(cont);
                contExpanded.put("wait", signalName);
                continuationSteps.add(contExpanded);
            }

            expandRecursive(continuationSteps, output, counter, locations);
        }
    }
}
