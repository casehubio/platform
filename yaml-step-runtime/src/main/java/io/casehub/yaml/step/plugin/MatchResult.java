package io.casehub.yaml.step.plugin;

public record MatchResult(boolean passed, String description) {

    public static MatchResult pass() {
        return new MatchResult(true, null);
    }

    public static MatchResult fail(String description) {
        return new MatchResult(false, description);
    }
}
