package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class SpringCrossTenantSubjectViewStore implements CrossTenantSubjectViewStore {

    private final SubjectViewEntityRepository repo;

    public SpringCrossTenantSubjectViewStore(SubjectViewEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findDistinctTenancyIds() {
        return repo.findDistinctTenancyIds();
    }
}
