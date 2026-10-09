package io.casehub.yaml.core.orchestration;

import java.util.function.DoubleConsumer;

public interface OrcNumericPrimitive extends OrcPrimitive {
    double doubleValue();
    void onThresholdChange(DoubleConsumer listener);
    void removeThresholdListener(DoubleConsumer listener);
}
