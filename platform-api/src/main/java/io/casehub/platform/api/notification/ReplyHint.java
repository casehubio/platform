package io.casehub.platform.api.notification;

import java.util.List;
import java.util.Objects;

public record ReplyHint(ReplyType type, List<String> options) {
    public ReplyHint {
        Objects.requireNonNull(type, "type");
        if (type == ReplyType.OPTIONS) {
            Objects.requireNonNull(options, "options required for OPTIONS type");
            if (options.isEmpty()) throw new IllegalArgumentException("options must not be empty");
            options = List.copyOf(options);
        } else {
            options = null;
        }
    }

    public static ReplyHint acknowledge() { return new ReplyHint(ReplyType.ACKNOWLEDGE, null); }
    public static ReplyHint freeText() { return new ReplyHint(ReplyType.FREE_TEXT, null); }
    public static ReplyHint options(List<String> options) { return new ReplyHint(ReplyType.OPTIONS, options); }
}
