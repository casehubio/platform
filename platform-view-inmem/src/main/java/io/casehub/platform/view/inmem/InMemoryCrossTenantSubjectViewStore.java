package io.casehub.platform.view.inmem;

import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryCrossTenantSubjectViewStore implements CrossTenantSubjectViewStore {

    @Override
    public List<String> findDistinctTenancyIds() {
        return List.of();
    }
}
