package io.casehub.template.registry;

import java.util.List;

@FunctionalInterface
public interface AdaptiveFallbackMatcher {

    ClassificationResult classify(
        String input,
        List<TemplateCandidate> candidates,
        String language);
}
