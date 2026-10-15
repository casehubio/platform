package io.casehub.yaml.step.handler;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class AriaInvokeHandler implements InvokeHandler {

    private final OutputStream out;

    public AriaInvokeHandler() {
        this(System.out);
    }

    public AriaInvokeHandler(OutputStream out) {
        this.out = out;
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Aria;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        InvokeBinding.Aria aria = (InvokeBinding.Aria) binding;
        String action = aria.action();
        return (params, services) -> {
            String line = format(action, params);
            writeLine(line);
            return Result.of(Map.of());
        };
    }

    private String format(String action, Map<String, Object> params) {
        return switch (action) {
            case "click", "expand", "collapse", "spotlight" -> targetOnly(action, params);
            case "fill", "select" -> targetWithValue(action, params);
            case "navigate" -> "[navigate] → " + str(params, "value");
            case "assert", "wait" -> targetWithState(action, params);
            case "show-markdown" -> formatShowMarkdown(params);
            case "editor-insert" -> formatEditorInsert(params);
            case "editor-replace" -> formatEditorReplace(params);
            case "editor-delete" -> formatEditorDelete(params);
            case "editor-cursor" -> formatEditorCursor(params);
            case "editor-highlight" -> formatEditorHighlight(params);
            case "editor-set-content" -> formatEditorSetContent(params);
            case "editor-completion" -> formatEditorCompletion(params);
            case "scroll-to-row" -> formatScrollToRow(params);
            default -> "[" + action + "] " + params;
        };
    }

    private String targetOnly(String action, Map<String, Object> params) {
        return "[" + action + "] " + str(params, "role") + " \"" + str(params, "name") + "\"";
    }

    private String targetWithValue(String action, Map<String, Object> params) {
        return targetOnly(action, params) + " ← \"" + str(params, "value") + "\"";
    }

    private String targetWithState(String action, Map<String, Object> params) {
        return targetOnly(action, params) + " state=" + formatState(params.get("state"));
    }

    private String formatShowMarkdown(Map<String, Object> params) {
        String content = str(params, "content");
        return "[show-markdown] (" + content.length() + " chars)";
    }

    private String formatEditorInsert(Map<String, Object> params) {
        return "[editor-insert] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " ← \"" + str(params, "value") + "\""
                + " at " + integer(params, "line") + ":" + integer(params, "col");
    }

    private String formatEditorReplace(Map<String, Object> params) {
        return "[editor-replace] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " range " + pos(params, "from") + "→" + pos(params, "to")
                + " ← \"" + str(params, "value") + "\"";
    }

    private String formatEditorDelete(Map<String, Object> params) {
        return "[editor-delete] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " range " + pos(params, "from") + "→" + pos(params, "to");
    }

    private String formatEditorCursor(Map<String, Object> params) {
        return "[editor-cursor] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " → " + integer(params, "line") + ":" + integer(params, "col");
    }

    private String formatEditorHighlight(Map<String, Object> params) {
        return "[editor-highlight] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " range " + pos(params, "from") + "→" + pos(params, "to");
    }

    private String formatEditorSetContent(Map<String, Object> params) {
        String value = str(params, "value");
        return "[editor-set-content] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " ← (" + value.length() + " chars)";
    }

    private String formatEditorCompletion(Map<String, Object> params) {
        return "[editor-completion] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " accept \"" + str(params, "label") + "\"";
    }

    private String formatScrollToRow(Map<String, Object> params) {
        return "[scroll-to-row] " + str(params, "role") + " \"" + str(params, "name") + "\""
                + " key=" + str(params, "key") + " value=\"" + str(params, "value") + "\"";
    }

    @SuppressWarnings("unchecked")
    private String pos(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw instanceof Map<?, ?> map) {
            return integer((Map<String, Object>) map, "line") + ":" + integer((Map<String, Object>) map, "col");
        }
        return "?:?";
    }

    @SuppressWarnings("unchecked")
    private String formatState(Object state) {
        if (state instanceof Map<?, ?> map) {
            String entries = ((Map<String, Object>) map).entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
            return "{" + entries + "}";
        }
        return String.valueOf(state);
    }

    private static String str(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? String.valueOf(val) : "";
    }

    private static int integer(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private void writeLine(String line) {
        try {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
