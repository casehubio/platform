package io.casehub.yaml.step.plugin;

@FunctionalInterface
public interface Matcher {
    MatchResult apply(Object subject, Object expected);
}
