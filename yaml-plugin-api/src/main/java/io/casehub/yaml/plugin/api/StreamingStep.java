package io.casehub.yaml.plugin.api;

import java.util.Map;

public interface StreamingStep<I, O> {

    String OUTPUT_KEY = "output";

    Class<I> inputType();

    Class<O> outputType();

    O apply(I input);

    default Result materialize(Map<String, Object> params, ServiceRegistry services) {
        throw new UnsupportedOperationException(
            getClass().getName() + " must override materialize() for standalone execution");
    }
}
