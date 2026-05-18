package io.casehub.platform.api.path;

import java.util.List;
import java.util.Objects;

public record Path(String value, List<String> segments) {

    public Path {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        segments = List.copyOf(segments);
    }

    public static Path of(String value) {
        Objects.requireNonNull(value, "Path must not be null");
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        String[] parts = trimmed.split("/", -1);
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException(
                    "Path segments must not be empty — check for leading, trailing, or consecutive slashes: \"" + value + "\"");
            }
        }
        return new Path(trimmed, List.of(parts));
    }

    public static Path of(String... segments) {
        Objects.requireNonNull(segments, "segments must not be null");
        if (segments.length == 0) {
            throw new IllegalArgumentException("Path must have at least one segment");
        }
        String[] stripped = new String[segments.length];
        for (int i = 0; i < segments.length; i++) {
            Objects.requireNonNull(segments[i], "segment must not be null");
            stripped[i] = segments[i].strip();
            if (stripped[i].isBlank()) {
                throw new IllegalArgumentException(
                    "Path segments must not be blank: \"" + segments[i] + "\"");
            }
        }
        String joined = String.join("/", stripped);
        return new Path(joined, List.of(stripped));
    }

    public Path parent() {
        if (segments.size() == 1) return null;
        List<String> parentSegments = segments.subList(0, segments.size() - 1);
        return new Path(String.join("/", parentSegments), List.copyOf(parentSegments));
    }

    public boolean isAncestorOf(Path other) {
        if (other.segments.size() <= this.segments.size()) return false;
        return other.segments.subList(0, this.segments.size()).equals(this.segments);
    }

    public int depth() {
        return segments.size();
    }
}
