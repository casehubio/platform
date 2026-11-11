package io.casehub.platform.graphql.generator.infer;

import java.util.List;
import java.util.Map;

public record InferredCallChain(
    String spiTypeFqcn,
    String routingParamName,
    String capabilityFqcn,
    String capabilityAccessor,
    List<String> accessorArgs,
    String targetMethod,
    List<String> targetArgs,
    Map<String, String> staticDefaults,
    String requireCapabilityMethodName,
    InferenceFailure failureReason
) {
    public boolean isComplete() {
        if (spiTypeFqcn == null || targetMethod == null) return false;
        if (capabilityFqcn != null && capabilityAccessor == null) return false;
        return failureReason == null;
    }
}
