package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.api.view.ViewMembershipTracker;
import io.casehub.platform.view.jpa.ViewMembershipEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpringViewMembershipTracker implements ViewMembershipTracker {

    private final ViewMembershipEntityRepository repo;

    public SpringViewMembershipTracker(ViewMembershipEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> getLastKnownMembership(UUID subjectId) {
        return repo.findBySubjectId(subjectId).stream()
                .collect(Collectors.toMap(e -> e.viewId, e -> e.viewName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<UUID, String>> getLastKnownMembership(Set<UUID> subjectIds) {
        if (subjectIds.isEmpty()) return Map.of();
        return repo.findBySubjectIdIn(subjectIds).stream()
                .collect(Collectors.groupingBy(
                        e -> e.subjectId,
                        Collectors.toMap(e -> e.viewId, e -> e.viewName)));
    }

    @Override
    @Transactional
    public void updateMembership(UUID subjectId, Map<UUID, String> viewIdToName) {
        repo.deleteBySubjectId(subjectId);
        repo.flush();

        viewIdToName.forEach((viewId, viewName) -> {
            var entity = new ViewMembershipEntity();
            entity.subjectId = subjectId;
            entity.viewId = viewId;
            entity.viewName = viewName;
            repo.save(entity);
        });
    }

    @Override
    @Transactional
    public void removeMembership(UUID subjectId) {
        repo.deleteBySubjectId(subjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getSubjectsByView(UUID viewId) {
        return new HashSet<>(repo.findSubjectIdsByViewId(viewId));
    }

    @Override
    @Transactional
    public void removeMembershipByView(UUID viewId) {
        repo.deleteByViewId(viewId);
    }
}
