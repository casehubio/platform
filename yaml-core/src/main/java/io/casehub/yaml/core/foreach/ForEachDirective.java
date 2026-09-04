package io.casehub.yaml.core.foreach;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface ForEachDirective permits ForEachDirective.GroupRef, ForEachDirective.InlineIteration {

    record GroupRef(String groupName, String as) implements ForEachDirective {
        public GroupRef(String groupName) {this(groupName, null);}
    }

    record InlineIteration(String as, List<?> in) implements ForEachDirective {
        public InlineIteration {
            Objects.requireNonNull(as, "Inline forEach 'as' variable name is required");
            if (in == null) {in = List.of();}
        }
    }

    @SuppressWarnings("unchecked")
    static ForEachDirective parse(Object raw) {
        if (raw == null) {return null;}
        if (raw instanceof ForEachDirective d) {return d;}
        if (raw instanceof String s) {return new GroupRef(s);}
        if (raw instanceof Map<?, ?> m) {
            String as = (String) m.get("as");
            Object in = m.get("in");
            if (in instanceof List<?> list) {return new InlineIteration(as, list);}
            if (in instanceof String ref) {return new GroupRef(ref, as);}
            if (as != null) {return new GroupRef(as);}
        }
        throw new IllegalArgumentException(
                "Invalid forEach value: expected string, {as, in} map, or ForEachDirective — got " + raw.getClass().getSimpleName());
    }
}
