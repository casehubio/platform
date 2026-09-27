package io.casehub.platform.api.process;

import java.util.List;

public record CommandPattern(List<String> segments, MatchMode mode) {

    public enum MatchMode { EXACT, PREFIX, GLOB }

    public CommandPattern {
        segments = List.copyOf(segments);
    }

    public boolean matches(List<String> command) {
        if (command.isEmpty()) return false;
        return switch (mode) {
            case EXACT -> command.equals(segments);
            case PREFIX -> matchPrefix(command);
            case GLOB -> matchGlob(command);
        };
    }

    private boolean matchPrefix(List<String> command) {
        if (command.size() < segments.size()) return false;
        for (int i = 0; i < segments.size(); i++) {
            if (i == segments.size() - 1) {
                return command.get(i).startsWith(segments.get(i));
            }
            if (!segments.get(i).equals(command.get(i))) return false;
        }
        return true;
    }

    private boolean matchGlob(List<String> command) {
        if (command.size() != segments.size()) return false;
        for (int i = 0; i < segments.size(); i++) {
            if (!globMatch(segments.get(i), command.get(i))) return false;
        }
        return true;
    }

    private static boolean globMatch(String pattern, String value) {
        if ("*".equals(pattern)) return true;
        if (!pattern.contains("*")) return pattern.equals(value);
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return value.matches(regex);
    }
}
