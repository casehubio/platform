package io.casehub.platform.api.label;

import java.util.Objects;

public sealed interface LabelAction permits LabelAction.Add, LabelAction.Remove {
    String label();

    record Add(String label) implements LabelAction {
        public Add {
            Objects.requireNonNull(label, "label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        }
    }

    record Remove(String label) implements LabelAction {
        public Remove {
            Objects.requireNonNull(label, "label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        }
    }
}
