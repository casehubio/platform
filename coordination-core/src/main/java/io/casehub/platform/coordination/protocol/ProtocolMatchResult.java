package io.casehub.platform.coordination.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public sealed interface ProtocolMatchResult {

    record Matched(String templateName, Map<String, String> params, double score) implements ProtocolMatchResult {
        public Matched {
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(params, "params");
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("Score must be between 0.0 and 1.0: " + score);
            }
        }

        public Matched(String templateName, Map<String, String> params) {
            this(templateName, params, 1.0);
        }
    }

    record Unmatched(String response) implements ProtocolMatchResult {
        public Unmatched {
            Objects.requireNonNull(response, "response");
        }
    }
}
