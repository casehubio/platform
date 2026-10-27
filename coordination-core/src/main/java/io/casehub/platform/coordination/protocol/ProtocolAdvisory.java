package io.casehub.platform.coordination.protocol;

public record ProtocolAdvisory(AdvisorySeverity severity, String message, SuggestedAction suggestedAction) {}
