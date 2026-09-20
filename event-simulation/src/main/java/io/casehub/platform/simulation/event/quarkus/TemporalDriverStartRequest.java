package io.casehub.platform.simulation.event.quarkus;

import java.util.List;

public record TemporalDriverStartRequest(
        String name,
        String profileName,
        String qualifiedName,
        String tenancyId,
        List<TemporalEventInput> events,
        Boolean loop,
        Double speed) {

    public String effectiveName() {
        if (name != null && !name.isBlank()) return name;
        if (profileName != null && !profileName.isBlank()) return profileName;
        throw new IllegalArgumentException("name or profileName is required");
    }

    public void validate() {
        if ((profileName == null || profileName.isBlank())
                && (qualifiedName == null || qualifiedName.isBlank())) {
            throw new IllegalArgumentException(
                    "Either profileName or qualifiedName must be provided");
        }
        if (profileName != null && !profileName.isBlank()
                && qualifiedName != null && !qualifiedName.isBlank()) {
            throw new IllegalArgumentException(
                    "profileName and qualifiedName are mutually exclusive");
        }
    }
}
