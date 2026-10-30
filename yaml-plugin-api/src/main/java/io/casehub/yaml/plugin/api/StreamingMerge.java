package io.casehub.yaml.plugin.api;

import java.util.List;

public interface StreamingMerge<T> {

    Class<T> mergeType();

    T merge(List<T> inputs);
}
