package io.casehub.yaml.step;

import java.util.Map;

public interface CatalogSource {

    void populate(Map<String, CatalogEntry> entries);

    int priority();
}
