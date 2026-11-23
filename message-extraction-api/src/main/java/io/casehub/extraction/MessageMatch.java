package io.casehub.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public sealed interface MessageMatch {

    record Matched(String type, int templateIndex, Map<String, String> fields, double score) implements MessageMatch {
        public Matched {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(fields, "fields");
            if (score <= 0.0 || score > 1.0) throw new IllegalArgumentException("Score must be in (0, 1.0]: " + score);
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    record Unmatched(String input) implements MessageMatch {
        public Unmatched {
            Objects.requireNonNull(input, "input");
        }
    }
}
