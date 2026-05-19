package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MapPreferencesTest {

    record TestSinglePref(String value) implements SingleValuePreference {
        static final TestSinglePref DEFAULT = new TestSinglePref("default");
        static final PreferenceKey<TestSinglePref> KEY = new PreferenceKey<>("test", "single", DEFAULT, TestSinglePref::new);
    }

    record TestMultiPref(String subKey, String value) implements MultiValuePreference {
        static final TestMultiPref DEFAULT = new TestMultiPref("default", "default");
        static final PreferenceKey<TestMultiPref> KEY = new PreferenceKey<>("test", "multi", DEFAULT, s -> new TestMultiPref("parsed", s));
    }

    @Test
    void get_returns_null_for_missing_key() {
        assertNull(new MapPreferences(Map.of()).get(TestSinglePref.KEY));
    }

    @Test
    void get_returns_typed_value_for_present_key() {
        TestSinglePref pref = new TestSinglePref("hello");
        MapPreferences prefs = new MapPreferences(Map.of("test.single", pref));
        assertEquals(pref, prefs.get(TestSinglePref.KEY));
    }

    @Test
    void get_with_subkey_returns_null_for_missing_key() {
        assertNull(new MapPreferences(Map.of()).get(TestMultiPref.KEY, "sub1"));
    }

    @Test
    void get_with_subkey_returns_typed_value() {
        TestMultiPref pref = new TestMultiPref("sub1", "val");
        MapPreferences prefs = new MapPreferences(Map.of("test.multi.sub1", pref));
        assertEquals(pref, prefs.get(TestMultiPref.KEY, "sub1"));
    }

    @Test
    void get_parses_string_value_using_key_parser() {
        MapPreferences prefs = new MapPreferences(Map.of("test.single", "hello"));
        TestSinglePref result = prefs.get(TestSinglePref.KEY);
        assertNotNull(result);
        assertEquals("hello", result.value());
    }

    @Test
    void get_with_subkey_parses_string_value_using_key_parser() {
        MapPreferences prefs = new MapPreferences(Map.of("test.multi.sub1", "parsedValue"));
        TestMultiPref result = prefs.get(TestMultiPref.KEY, "sub1");
        assertNotNull(result);
        assertEquals("parsedValue", result.value());
    }

    @Test
    void asMap_returns_all_values() {
        Map<String, Object> values = Map.of("test.single", "strVal");
        assertEquals(values, new MapPreferences(values).asMap());
    }

    @Test
    void asMap_is_unmodifiable() {
        MapPreferences prefs = new MapPreferences(new HashMap<>(Map.of("k", "v")));
        assertThrows(UnsupportedOperationException.class, () -> prefs.asMap().put("new", "value"));
    }

    @Test
    void getOrDefault_returns_default_when_key_absent() {
        MapPreferences prefs = new MapPreferences(Map.of());
        assertSame(TestSinglePref.DEFAULT, prefs.getOrDefault(TestSinglePref.KEY));
    }

    @Test
    void getOrDefault_returns_value_when_key_present() {
        TestSinglePref pref = new TestSinglePref("hello");
        MapPreferences prefs = new MapPreferences(Map.of("test.single", pref));
        assertEquals(pref, prefs.getOrDefault(TestSinglePref.KEY));
    }
}
