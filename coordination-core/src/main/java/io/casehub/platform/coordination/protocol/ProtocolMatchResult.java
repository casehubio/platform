package io.casehub.platform.coordination.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public sealed interface ProtocolMatchResult {

    record Matched(String templateName, Map<String, String> params) implements ProtocolMatchResult {
        public Matched {
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(params, "params");
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    record Unmatched(String response) implements ProtocolMatchResult {
        public Unmatched {
            Objects.requireNonNull(response, "response");
        }
    }
}
