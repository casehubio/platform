package io.casehub.platform.api.path;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strategy for parsing a raw string into path segments.
 * The default implementation splits on {@code /} and can be replaced
 * platform-wide via {@code Path.setDefaultParser(PathParser)}.
 */
@FunctionalInterface
public interface PathParser {
    /**
     * Splits {@code raw} into segments. Implementations must not strip whitespace
     * or filter blanks — Path.parse() handles validation after calling this.
     */
    String[] parse(String raw);

    /** Returns a parser that splits on the given literal separator. */
    static PathParser of(String separator) {
        Objects.requireNonNull(separator, "separator must not be null");
        if (separator.isEmpty()) throw new IllegalArgumentException("separator must not be empty");
        String pattern = Pattern.quote(separator);
        return raw -> raw.split(pattern, -1);
    }
}
