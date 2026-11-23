package io.casehub.platform.coordination.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface NlpProtocolMatcher {

    record NlpMatch(String templateName, Map<String, String> params, double score) {
        public NlpMatch {
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(params, "params");
            params = Map.copyOf(params);
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("Score must be between 0.0 and 1.0: " + score);
            }
        }
    }

    Optional<NlpMatch> match(Map<String, String> templates, String input);
}
