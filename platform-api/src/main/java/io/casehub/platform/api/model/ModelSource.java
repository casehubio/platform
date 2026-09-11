package io.casehub.platform.api.model;

import java.util.List;

public interface ModelSource {
    String sourceId();
    int priority();
    List<ModelDescriptor> refresh();
}
