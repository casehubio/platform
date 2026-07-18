package io.casehub.platform.view;

import io.casehub.platform.api.view.LabelPatternMatcher;
import io.casehub.platform.api.view.SubjectViewEvent;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.ViewEventType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SubjectViewEvaluator {

    public Map<UUID, String> evaluateMembership(
            Set<String> subjectLabelPaths,
            List<SubjectViewSpec> views) {
        return views.stream()
            .filter(v -> subjectLabelPaths.stream()
                .anyMatch(p -> LabelPatternMatcher.matches(v.labelPattern(), p)))
            .collect(Collectors.toMap(SubjectViewSpec::id, SubjectViewSpec::name));
    }

    public List<SubjectViewEvent> diff(
            UUID subjectId,
            String tenancyId,
            Map<UUID, String> before,
            Map<UUID, String> after) {
        List<SubjectViewEvent> events = new ArrayList<>();

        before.forEach((id, name) -> {
            if (!after.containsKey(id)) {
                events.add(new SubjectViewEvent(subjectId, id, name,
                    ViewEventType.REMOVED, tenancyId));
            }
        });

        after.forEach((id, name) -> {
            if (!before.containsKey(id)) {
                events.add(new SubjectViewEvent(subjectId, id, name,
                    ViewEventType.ADDED, tenancyId));
            }
        });

        return events;
    }
}
