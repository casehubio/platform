package io.casehub.platform.api.path;

import java.util.List;
import java.util.Objects;

public record Path(String value, List<String> segments) {

    public Path {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        segments = List.copyOf(segments);
    }

    private static volatile PathParser defaultParser = PathParser.of("/");

    /** Replaces the default parser used by {@link #parse(String)}. Called by the Quarkus platform layer at startup. */
    public static void setDefaultParser(PathParser parser) {
        defaultParser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /**
     * Parses a raw string into a Path using the platform-configured default parser.
     * The default separator is {@code /}; override via {@code casehub.platform.path.separator}.
     */
    public static Path parse(String raw) {
        return parse(raw, defaultParser);
    }

    /**
     * Parses a raw string into a Path using the given parser.
     * The parser is responsible for splitting — this method validates the resulting segments.
     */
    public static Path parse(String raw, PathParser parser) {
        Objects.requireNonNull(raw, "raw must not be null");
        Objects.requireNonNull(parser, "parser must not be null");
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Path must not be blank");
        String[] parts = parser.parse(trimmed);
        for (String part : parts) {
            if (part.isBlank()) throw new IllegalArgumentException(
                "Path segments must not be empty after parsing: \"" + raw + "\"");
        }
        return new Path(trimmed, List.of(parts));
    }

    private static final Path ROOT = new Path("", List.of());

    /**
     * Returns the root path — zero segments, represents the org-wide scope.
     * Use as the fallback when no scope has been assigned to a work unit.
     *
     * <p>Root is the ancestor of every non-root path. {@link #isAncestorOf} and
     * {@link #parent} both handle root correctly.
     */
    public static Path root() {
        return ROOT;
    }

    /**
     * Constructs a Path from explicit segments. No parsing — each segment is used as-is
     * after stripping and blank validation. Prefer this over {@link #parse(String)} when
     * building paths programmatically.
     */
    public static Path of(String... segments) {
        Objects.requireNonNull(segments, "segments must not be null");
        if (segments.length == 0) throw new IllegalArgumentException("Path must have at least one segment");
        String[] stripped = new String[segments.length];
        for (int i = 0; i < segments.length; i++) {
            Objects.requireNonNull(segments[i], "segment must not be null");
            stripped[i] = segments[i].strip();
            if (stripped[i].isBlank()) throw new IllegalArgumentException(
                "Path segments must not be blank: \"" + segments[i] + "\"");
        }
        String joined = String.join("/", stripped);
        return new Path(joined, List.of(stripped));
    }

    /** Returns the parent path, or {@code null} if this path is a root or single-segment path. */
    public Path parent() {
        if (segments.size() <= 1) return null;
        List<String> parentSegments = segments.subList(0, segments.size() - 1);
        return new Path(String.join("/", parentSegments), List.copyOf(parentSegments));
    }

    /**
     * Returns true if this path is a strict ancestor of {@code other}.
     * Root (zero segments) is an ancestor of every non-root path.
     */
    public boolean isAncestorOf(Path other) {
        if (this.segments.isEmpty()) return !other.segments.isEmpty();
        if (other.segments.size() <= this.segments.size()) return false;
        return other.segments.subList(0, this.segments.size()).equals(this.segments);
    }

    public int depth() { return segments.size(); }
}
