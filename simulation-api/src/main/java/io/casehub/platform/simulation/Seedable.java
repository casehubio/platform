package io.casehub.platform.simulation;

import java.util.List;
import java.util.Map;

public interface Seedable {
    void seed(String qualifiedMethodName, List<Map<String, Object>> corpusOutputs);
}
