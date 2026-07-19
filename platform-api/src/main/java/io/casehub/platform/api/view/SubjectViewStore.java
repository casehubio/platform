package io.casehub.platform.api.view;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectViewStore {
    SubjectViewSpec save(SubjectViewSpec spec);
    Optional<SubjectViewSpec> findById(UUID id);
    List<SubjectViewSpec> findByTenancy(String tenancyId);

    boolean delete(UUID id);
}
