package io.casehub.platform.api.capacity;

public interface RedistributionPolicy {

    RedistributionDecision evaluate(RedistributionContext context);
}
