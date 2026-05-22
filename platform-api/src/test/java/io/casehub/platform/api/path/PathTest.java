package io.casehub.platform.api.path;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void parse_splits_on_slash() {
        Path p = Path.parse("acme/backend/pr-review");
        assertEquals("acme/backend/pr-review", p.value());
        assertEquals(List.of("acme", "backend", "pr-review"), p.segments());
    }

    @Test
    void parse_strips_outer_whitespace() {
        Path p = Path.parse("  acme/backend  ");
        assertEquals("acme/backend", p.value());
    }

    @Test
    void parse_single_segment() {
        Path p = Path.parse("root");
        assertEquals("root", p.value());
        assertEquals(List.of("root"), p.segments());
    }

    @Test
    void parse_throws_on_blank() {
        assertThrows(IllegalArgumentException.class, () -> Path.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Path.parse("   "));
    }

    @Test
    void parse_throws_on_leading_slash() {
        assertThrows(IllegalArgumentException.class, () -> Path.parse("/acme/backend"));
    }

    @Test
    void parse_throws_on_trailing_slash() {
        assertThrows(IllegalArgumentException.class, () -> Path.parse("acme/backend/"));
    }

    @Test
    void parse_throws_on_consecutive_slashes() {
        assertThrows(IllegalArgumentException.class, () -> Path.parse("acme//backend"));
    }

    @Test
    void parse_with_explicit_parser_splits_on_custom_separator() {
        PathParser dotParser = PathParser.of(".");
        Path p = Path.parse("acme.backend.pr-review", dotParser);
        assertEquals("acme.backend.pr-review", p.value());
        assertEquals(List.of("acme", "backend", "pr-review"), p.segments());
    }

    @Test
    void setDefaultParser_changes_parse_behaviour() {
        PathParser original = PathParser.of("/");
        try {
            Path.setDefaultParser(PathParser.of("."));
            Path p = Path.parse("acme.backend");
            assertEquals(List.of("acme", "backend"), p.segments());
        } finally {
            Path.setDefaultParser(original); // always restore
        }
    }

    @Test
    void of_varargs_joins_with_slash() {
        Path p = Path.of("acme", "backend", "pr-review");
        assertEquals("acme/backend/pr-review", p.value());
        assertEquals(List.of("acme", "backend", "pr-review"), p.segments());
    }

    @Test
    void of_varargs_throws_on_empty_segment() {
        assertThrows(IllegalArgumentException.class, () -> Path.of("acme", "", "backend"));
    }

    @Test
    void of_varargs_throws_on_blank_segment() {
        assertThrows(IllegalArgumentException.class, () -> Path.of("acme", "  ", "backend"));
    }

    @Test
    void parent_returns_all_but_last_segment() {
        Path p = Path.parse("acme/backend/pr-review");
        Path parent = p.parent();
        assertEquals("acme/backend", parent.value());
        assertEquals(List.of("acme", "backend"), parent.segments());
    }

    @Test
    void parent_returns_null_at_root() {
        assertNull(Path.parse("root").parent());
    }

    @Test
    void depth_equals_segment_count() {
        assertEquals(3, Path.parse("a/b/c").depth());
        assertEquals(1, Path.parse("root").depth());
    }

    @Test
    void isAncestorOf_returns_true_for_prefix() {
        assertTrue(Path.parse("acme/backend").isAncestorOf(Path.parse("acme/backend/pr-review")));
    }

    @Test
    void isAncestorOf_returns_false_for_same_path() {
        Path p = Path.parse("acme/backend");
        assertFalse(p.isAncestorOf(p));
    }

    @Test
    void isAncestorOf_returns_false_for_non_prefix() {
        assertFalse(Path.parse("acme/backend").isAncestorOf(Path.parse("acme/frontend")));
    }

    @Test
    void isAncestorOf_returns_false_when_other_is_shorter() {
        assertFalse(Path.parse("acme/backend/pr-review").isAncestorOf(Path.parse("acme/backend")));
    }

    @Test
    void isAncestorOf_returns_false_for_partial_name_match() {
        // "acme/back" is NOT an ancestor of "acme/backend" — segment comparison, not string prefix
        assertFalse(Path.parse("acme/back").isAncestorOf(Path.parse("acme/backend")));
    }

    @Test
    void of_varargs_throws_on_null_element() {
        assertThrows(NullPointerException.class, () -> Path.of("acme", null, "backend"));
    }

    @Test
    void root_has_zero_segments_and_empty_value() {
        Path root = Path.root();
        assertEquals("", root.value());
        assertEquals(List.of(), root.segments());
        assertEquals(0, root.depth());
    }

    @Test
    void root_parent_returns_null() {
        assertNull(Path.root().parent());
    }

    @Test
    void root_isAncestorOf_non_root_returns_true() {
        assertTrue(Path.root().isAncestorOf(Path.parse("acme")));
        assertTrue(Path.root().isAncestorOf(Path.parse("acme/backend/pr-review")));
    }

    @Test
    void root_isAncestorOf_root_returns_false() {
        assertFalse(Path.root().isAncestorOf(Path.root()));
    }

    @Test
    void non_root_isAncestorOf_root_returns_false() {
        assertFalse(Path.parse("acme").isAncestorOf(Path.root()));
    }

    @Test
    void root_returns_same_instance() {
        assertSame(Path.root(), Path.root());
    }
}
