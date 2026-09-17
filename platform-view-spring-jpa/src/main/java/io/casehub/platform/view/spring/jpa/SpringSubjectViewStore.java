package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.platform.view.jpa.SubjectViewEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpringSubjectViewStore implements SubjectViewStore {

    private final SubjectViewEntityRepository repo;

    public SpringSubjectViewStore(SubjectViewEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public SubjectViewSpec save(SubjectViewSpec spec) {
        SubjectViewEntity entity = SubjectViewEntity.fromSpec(spec);
        entity = repo.save(entity);
        repo.flush();
        return entity.toSpec();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectViewSpec> findById(UUID id) {
        return repo.findById(id).map(SubjectViewEntity::toSpec);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectViewSpec> findByTenancy(String tenancyId) {
        return repo.findByTenancyId(tenancyId).stream()
                .map(SubjectViewEntity::toSpec)
                .toList();
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        if (repo.existsById(id)) {
            repo.deleteById(id);
            return true;
        }
        return false;
    }
}
