package io.casehub.yaml.step.plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MatcherRegistry {

    private static final Map<String, Matcher> MATCHERS = new ConcurrentHashMap<>();

    static {
        MATCHERS.put("contains", MatcherRegistry::matchContains);
        MATCHERS.put("equals", MatcherRegistry::matchEquals);
        MATCHERS.put("matches", MatcherRegistry::matchRegex);
        MATCHERS.put("hasSize", MatcherRegistry::matchHasSize);
        MATCHERS.put("startsWith", MatcherRegistry::matchStartsWith);
        MATCHERS.put("endsWith", MatcherRegistry::matchEndsWith);
        MATCHERS.put("greaterThan", MatcherRegistry::matchGreaterThan);
        MATCHERS.put("greaterThanOrEqual", MatcherRegistry::matchGreaterThanOrEqual);
        MATCHERS.put("lessThan", MatcherRegistry::matchLessThan);
        MATCHERS.put("not-null", (s, e) -> s != null ? MatchResult.pass() : MatchResult.fail("not-null: value is null"));
        MATCHERS.put("not-empty", MatcherRegistry::matchNotEmpty);
        MATCHERS.put("empty", MatcherRegistry::matchEmpty);
    }

    private MatcherRegistry() {}

    public static Optional<Matcher> resolve(String name) {
        return Optional.ofNullable(MATCHERS.get(name));
    }

    public static boolean isMatcher(String name) {
        return MATCHERS.containsKey(name);
    }

    private static MatchResult matchContains(Object subject, Object expected) {
        if (subject == null) {
            return MatchResult.fail("contains: subject is null");
        }
        String expectedStr = String.valueOf(expected);
        if (subject instanceof String s) {
            return s.contains(expectedStr)
                    ? MatchResult.pass()
                    : MatchResult.fail("contains: '" + expectedStr + "' — actual value does not contain '" + expectedStr + "'");
        }
        if (subject instanceof Collection<?> c) {
            return c.contains(expected)
                    ? MatchResult.pass()
                    : MatchResult.fail("contains: " + expected + " — collection does not contain element");
        }
        String subjectStr = String.valueOf(subject);
        return subjectStr.contains(expectedStr)
                ? MatchResult.pass()
                : MatchResult.fail("contains: '" + expectedStr + "' — actual value does not contain '" + expectedStr + "'");
    }

    private static MatchResult matchEquals(Object subject, Object expected) {
        if (subject == null && expected == null) return MatchResult.pass();
        if (subject != null && subject.equals(expected)) return MatchResult.pass();
        if (subject != null && expected != null && String.valueOf(subject).equals(String.valueOf(expected))) {
            return MatchResult.pass();
        }
        return MatchResult.fail("equals: '" + expected + "' — actual was '" + subject + "'");
    }

    private static MatchResult matchRegex(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("matches: subject is null");
        String pattern = String.valueOf(expected);
        return Pattern.matches(pattern, String.valueOf(subject))
                ? MatchResult.pass()
                : MatchResult.fail("matches: '" + pattern + "' — actual '" + subject + "' does not match");
    }

    private static MatchResult matchHasSize(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("hasSize: subject is null");
        int expectedSize = expected instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(expected));
        int actualSize;
        if (subject instanceof Collection<?> c) {
            actualSize = c.size();
        } else if (subject instanceof Map<?, ?> m) {
            actualSize = m.size();
        } else if (subject instanceof String s) {
            actualSize = s.length();
        } else {
            return MatchResult.fail("hasSize: subject is not a collection, map, or string");
        }
        return actualSize == expectedSize
                ? MatchResult.pass()
                : MatchResult.fail("hasSize: " + expectedSize + " — actual size was " + actualSize);
    }

    private static MatchResult matchStartsWith(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("startsWith: subject is null");
        String s = String.valueOf(subject);
        String prefix = String.valueOf(expected);
        return s.startsWith(prefix)
                ? MatchResult.pass()
                : MatchResult.fail("startsWith: '" + prefix + "' — actual value starts with '" + s.substring(0, Math.min(s.length(), prefix.length())) + "'");
    }

    private static MatchResult matchEndsWith(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("endsWith: subject is null");
        String s = String.valueOf(subject);
        String suffix = String.valueOf(expected);
        return s.endsWith(suffix)
                ? MatchResult.pass()
                : MatchResult.fail("endsWith: '" + suffix + "' — actual value ends with '" + s.substring(Math.max(0, s.length() - suffix.length())) + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MatchResult matchGreaterThan(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("greaterThan: subject is null");
        try {
            Comparable s = toComparable(subject);
            Comparable e = toComparable(expected);
            return s.compareTo(e) > 0
                    ? MatchResult.pass()
                    : MatchResult.fail("greaterThan: " + expected + " — actual was " + subject);
        } catch (Exception ex) {
            return MatchResult.fail("greaterThan: cannot compare — " + ex.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MatchResult matchGreaterThanOrEqual(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("greaterThanOrEqual: subject is null");
        try {
            Comparable s = toComparable(subject);
            Comparable e = toComparable(expected);
            return s.compareTo(e) >= 0
                    ? MatchResult.pass()
                    : MatchResult.fail("greaterThanOrEqual: " + expected + " — actual was " + subject);
        } catch (Exception ex) {
            return MatchResult.fail("greaterThanOrEqual: cannot compare — " + ex.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MatchResult matchLessThan(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("lessThan: subject is null");
        try {
            Comparable s = toComparable(subject);
            Comparable e = toComparable(expected);
            return s.compareTo(e) < 0
                    ? MatchResult.pass()
                    : MatchResult.fail("lessThan: " + expected + " — actual was " + subject);
        } catch (Exception ex) {
            return MatchResult.fail("lessThan: cannot compare — " + ex.getMessage());
        }
    }

    private static Comparable<?> toComparable(Object value) {
        if (value instanceof Comparable<?> c) return c;
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        throw new IllegalArgumentException("Not comparable: " + value);
    }

    private static MatchResult matchNotEmpty(Object subject, Object expected) {
        if (subject == null) return MatchResult.fail("not-empty: subject is null");
        if (subject instanceof String s && s.isEmpty()) return MatchResult.fail("not-empty: string is empty");
        if (subject instanceof Collection<?> c && c.isEmpty()) return MatchResult.fail("not-empty: collection is empty");
        if (subject instanceof Map<?, ?> m && m.isEmpty()) return MatchResult.fail("not-empty: map is empty");
        return MatchResult.pass();
    }

    private static MatchResult matchEmpty(Object subject, Object expected) {
        if (subject == null) return MatchResult.pass();
        if (subject instanceof String s && s.isEmpty()) return MatchResult.pass();
        if (subject instanceof Collection<?> c && c.isEmpty()) return MatchResult.pass();
        if (subject instanceof Map<?, ?> m && m.isEmpty()) return MatchResult.pass();
        return MatchResult.fail("empty: subject is not empty");
    }
}
