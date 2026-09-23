package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    private DurationParser() {}

    public static Duration parse(String input) {
        Matcher matcher = PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid duration: '" + input.trim() + "'. Expected format: <number><ms|s|m|h>");
        }
        long value = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            default -> throw new IllegalArgumentException("Unknown suffix: " + matcher.group(2));
        };
    }
}
