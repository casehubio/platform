package io.casehub.platform.api.path;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    void of_string_splits_on_slash() {
        Path p = Path.of("acme/backend/pr-review");
        assertEquals("acme/backend/pr-review", p.value());
        assertEquals(List.of("acme", "backend", "pr-review"), p.segments());
    }

    @Test
    void of_string_strips_outer_whitespace() {
        Path p = Path.of("  acme/backend  ");
        assertEquals("acme/backend", p.value());
    }

    @Test
    void of_string_single_segment() {
        Path p = Path.of("root");
        assertEquals("root", p.value());
        assertEquals(List.of("root"), p.segments());
    }

    @Test
    void of_string_throws_on_blank() {
        assertThrows(IllegalArgumentException.class, () -> Path.of(""));
        assertThrows(IllegalArgumentException.class, () -> Path.of("   "));
    }

    @Test
    void of_string_throws_on_leading_slash() {
        assertThrows(IllegalArgumentException.class, () -> Path.of("/acme/backend"));
    }

    @Test
    void of_string_throws_on_trailing_slash() {
        assertThrows(IllegalArgumentException.class, () -> Path.of("acme/backend/"));
    }

    @Test
    void of_string_throws_on_consecutive_slashes() {
        assertThrows(IllegalArgumentException.class, () -> Path.of("acme//backend"));
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
        Path p = Path.of("acme/backend/pr-review");
        Path parent = p.parent();
        assertEquals("acme/backend", parent.value());
        assertEquals(List.of("acme", "backend"), parent.segments());
    }

    @Test
    void parent_returns_null_at_root() {
        assertNull(Path.of("root").parent());
    }

    @Test
    void depth_equals_segment_count() {
        assertEquals(3, Path.of("a/b/c").depth());
        assertEquals(1, Path.of("root").depth());
    }

    @Test
    void isAncestorOf_returns_true_for_prefix() {
        assertTrue(Path.of("acme/backend").isAncestorOf(Path.of("acme/backend/pr-review")));
    }

    @Test
    void isAncestorOf_returns_false_for_same_path() {
        Path p = Path.of("acme/backend");
        assertFalse(p.isAncestorOf(p));
    }

    @Test
    void isAncestorOf_returns_false_for_non_prefix() {
        assertFalse(Path.of("acme/backend").isAncestorOf(Path.of("acme/frontend")));
    }

    @Test
    void isAncestorOf_returns_false_when_other_is_shorter() {
        assertFalse(Path.of("acme/backend/pr-review").isAncestorOf(Path.of("acme/backend")));
    }

    @Test
    void isAncestorOf_returns_false_for_partial_name_match() {
        // "acme/back" is NOT an ancestor of "acme/backend" — segment comparison, not string prefix
        assertFalse(Path.of("acme/back").isAncestorOf(Path.of("acme/backend")));
    }

    @Test
    void of_varargs_throws_on_null_element() {
        assertThrows(NullPointerException.class, () -> Path.of("acme", null, "backend"));
    }
}
