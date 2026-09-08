package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.RedistributionContext;
import io.casehub.platform.api.capacity.RedistributionDecision;
import io.casehub.platform.api.capacity.RedistributionPolicy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class DefaultRedistributionPolicy implements RedistributionPolicy {

    private final double compressThreshold;
    private final double redistributeThreshold;
    private final double immediateThreshold;
    private final Duration gracePeriod;
    private final Duration inactivityEscalation;

    public DefaultRedistributionPolicy(
            @ConfigProperty(name = "casehub.capacity.redistribution.compress-threshold",
                            defaultValue = "0.7") double compressThreshold,
            @ConfigProperty(name = "casehub.capacity.redistribution.redistribute-threshold",
                            defaultValue = "0.85") double redistributeThreshold,
            @ConfigProperty(name = "casehub.capacity.redistribution.immediate-threshold",
                            defaultValue = "0.95") double immediateThreshold,
            @ConfigProperty(name = "casehub.capacity.redistribution.grace-period",
                            defaultValue = "30s") Duration gracePeriod,
            @ConfigProperty(name = "casehub.capacity.redistribution.inactivity-escalation",
                            defaultValue = "5m") Duration inactivityEscalation) {
        this.compressThreshold = compressThreshold;
        this.redistributeThreshold = redistributeThreshold;
        this.immediateThreshold = immediateThreshold;
        this.gracePeriod = gracePeriod;
        this.inactivityEscalation = inactivityEscalation;
    }

    @Override
    public RedistributionDecision evaluate(RedistributionContext context) {
        double pressure = context.capacity().aggregatePressure();

        if (context.timeSinceLastActivity().compareTo(inactivityEscalation) >= 0) {
            return RedistributionDecision.escalate(
                    "inactive for " + context.timeSinceLastActivity());
        }

        if (pressure >= immediateThreshold && context.openObligationCount() > 0) {
            return new RedistributionDecision.Redistribute(
                    "pressure " + pressure + " exceeds immediate threshold " + immediateThreshold,
                    Duration.ZERO, Set.of(context.actorId()));
        }
        if (pressure >= redistributeThreshold && context.openObligationCount() > 0) {
            return new RedistributionDecision.Redistribute(
                    "pressure " + pressure + " exceeds redistribute threshold " + redistributeThreshold,
                    gracePeriod, Set.of(context.actorId()));
        }
        if (pressure >= redistributeThreshold && context.openObligationCount() == 0) {
            return RedistributionDecision.hold(
                    "pressure " + pressure + " but no movable obligations");
        }
        if (pressure >= compressThreshold) {
            return RedistributionDecision.compress(
                    "pressure " + pressure + " exceeds compress threshold " + compressThreshold);
        }
        return RedistributionDecision.hold("pressure " + pressure + " below all thresholds");
    }
}
