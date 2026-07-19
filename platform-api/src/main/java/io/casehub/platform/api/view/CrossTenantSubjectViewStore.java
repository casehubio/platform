package io.casehub.platform.api.view;

import java.util.List;

public interface CrossTenantSubjectViewStore {
    List<String> findDistinctTenancyIds();
}
