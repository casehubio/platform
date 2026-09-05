package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.RedistributionContext;
import io.casehub.platform.api.capacity.RedistributionDecision;
import io.casehub.platform.api.capacity.RedistributionPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DefaultRedistributionPolicy implements RedistributionPolicy {

    private final double compressThreshold;
    private final double redistributeThreshold;
    private final double escalateThreshold;

    public DefaultRedistributionPolicy(
            @ConfigProperty(name = "casehub.capacity.threshold.compress",
                            defaultValue = "0.7") double compressThreshold,
            @ConfigProperty(name = "casehub.capacity.threshold.redistribute",
                            defaultValue = "0.85") double redistributeThreshold,
            @ConfigProperty(name = "casehub.capacity.threshold.escalate",
                            defaultValue = "0.95") double escalateThreshold) {
        this.compressThreshold = compressThreshold;
        this.redistributeThreshold = redistributeThreshold;
        this.escalateThreshold = escalateThreshold;
    }

    @Override
    public RedistributionDecision evaluate(RedistributionContext context) {
        double pressure = context.capacity().aggregatePressure();

        if (pressure >= escalateThreshold) {
            return RedistributionDecision.escalate(
                    "pressure " + pressure + " exceeds escalate threshold "
                    + escalateThreshold);
        }
        if (pressure >= redistributeThreshold) {
            return RedistributionDecision.redistribute(
                    "pressure " + pressure + " exceeds redistribute threshold "
                    + redistributeThreshold);
        }
        if (pressure >= compressThreshold) {
            return RedistributionDecision.compress(
                    "pressure " + pressure + " exceeds compress threshold "
                    + compressThreshold);
        }
        return RedistributionDecision.hold("pressure " + pressure + " below all thresholds");
    }
}
