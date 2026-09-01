package io.casehub.yaml.core.foreach;

import java.util.List;
import java.util.Objects;

public sealed interface ForEachDirective permits ForEachDirective.GroupRef, ForEachDirective.InlineIteration {

    record GroupRef(String groupName) implements ForEachDirective {}

    record InlineIteration(String as, List<?> in) implements ForEachDirective {
        public InlineIteration {
            Objects.requireNonNull(as, "Inline forEach 'as' variable name is required");
            if (in == null) { in = List.of(); }
        }
    }
}
