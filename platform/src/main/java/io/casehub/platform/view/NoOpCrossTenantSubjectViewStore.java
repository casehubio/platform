package io.casehub.platform.view;

import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpCrossTenantSubjectViewStore implements CrossTenantSubjectViewStore {

    @Override
    public List<String> findDistinctTenancyIds() {
        return List.of();
    }
}
