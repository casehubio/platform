package io.casehub.yaml.step.handler;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AriaInvokeHandlerTest {

    private ByteArrayOutputStream out;
    private AriaInvokeHandler handler;
    private final MapServiceRegistry services = new MapServiceRegistry();

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        handler = new AriaInvokeHandler(out);
    }

    @Test
    void supportsAriaBinding() {
        assertThat(handler.supports(new InvokeBinding.Aria("click"))).isTrue();
    }

    @Test
    void doesNotSupportOtherBindings() {
        assertThat(handler.supports(new InvokeBinding.Mcp("tool"))).isFalse();
        assertThat(handler.supports(new InvokeBinding.Graphql("q"))).isFalse();
    }

    @Test
    void click() {
        Result result = execute("click", Map.of("role", "button", "name", "A"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[click] button \"A\"");
    }

    @Test
    void fill() {
        Result result = execute("fill", Map.of("role", "textbox", "name", "Email", "value", "alice@example.com"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[fill] textbox \"Email\" ← \"alice@example.com\"");
    }

    @Test
    void select() {
        Result result = execute("select", Map.of("role", "listbox", "name", "Department", "value", "Engineering"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[select] listbox \"Department\" ← \"Engineering\"");
    }

    @Test
    void navigate() {
        Result result = execute("navigate", Map.of("value", "/dashboard"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[navigate] → /dashboard");
    }

    @Test
    void expand() {
        Result result = execute("expand", Map.of("role", "treeitem", "name", "Settings"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[expand] treeitem \"Settings\"");
    }

    @Test
    void collapse() {
        Result result = execute("collapse", Map.of("role", "treeitem", "name", "Settings"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[collapse] treeitem \"Settings\"");
    }

    @Test
    void assertAction() {
        Result result = execute("assert", Map.of("role", "button", "name", "Submit", "state", Map.of("disabled", true)));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[assert] button \"Submit\" state={disabled: true}");
    }

    @Test
    void waitAction() {
        Result result = execute("wait", Map.of("role", "progressbar", "name", "Loading", "state", Map.of("value", 100)));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[wait] progressbar \"Loading\" state={value: 100}");
    }

    @Test
    void spotlight() {
        Result result = execute("spotlight", Map.of("role", "button", "name", "Next"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[spotlight] button \"Next\"");
    }

    @Test
    void showMarkdown() {
        String content = "x".repeat(142);
        Result result = execute("show-markdown", Map.of("content", content));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[show-markdown] (142 chars)");
    }

    @Test
    void editorInsert() {
        Result result = execute("editor-insert", Map.of("role", "editor", "name", "Code", "value", "const x = 1", "line", 3, "col", 1));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-insert] editor \"Code\" ← \"const x = 1\" at 3:1");
    }

    @Test
    void editorReplace() {
        Result result = execute("editor-replace", Map.of("role", "editor", "name", "Code",
                "from", Map.of("line", 1, "col", 0), "to", Map.of("line", 1, "col", 5), "value", "const"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-replace] editor \"Code\" range 1:0→1:5 ← \"const\"");
    }

    @Test
    void editorDelete() {
        Result result = execute("editor-delete", Map.of("role", "editor", "name", "Code",
                "from", Map.of("line", 2, "col", 0), "to", Map.of("line", 2, "col", 10)));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-delete] editor \"Code\" range 2:0→2:10");
    }

    @Test
    void editorCursor() {
        Result result = execute("editor-cursor", Map.of("role", "editor", "name", "Code", "line", 5, "col", 12));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-cursor] editor \"Code\" → 5:12");
    }

    @Test
    void editorHighlight() {
        Result result = execute("editor-highlight", Map.of("role", "editor", "name", "Code",
                "from", Map.of("line", 1, "col", 0), "to", Map.of("line", 3, "col", 0)));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-highlight] editor \"Code\" range 1:0→3:0");
    }

    @Test
    void editorSetContent() {
        String content = "x".repeat(87);
        Result result = execute("editor-set-content", Map.of("role", "editor", "name", "Code", "value", content));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-set-content] editor \"Code\" ← (87 chars)");
    }

    @Test
    void editorCompletion() {
        Result result = execute("editor-completion", Map.of("role", "editor", "name", "Code", "label", "useState"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[editor-completion] editor \"Code\" accept \"useState\"");
    }

    @Test
    void scrollToRow() {
        Result result = execute("scroll-to-row", Map.of("role", "grid", "name", "Roster", "key", "id", "value", "42"));
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(line()).isEqualTo("[scroll-to-row] grid \"Roster\" key=id value=\"42\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"click", "fill", "select", "navigate", "expand", "collapse",
            "assert", "wait", "spotlight", "show-markdown",
            "editor-insert", "editor-replace", "editor-delete", "editor-cursor",
            "editor-highlight", "editor-set-content", "editor-completion", "scroll-to-row"})
    void allActionsReturnSuccess(String action) {
        Result result = execute(action, Map.ofEntries(
                Map.entry("role", "button"), Map.entry("name", "x"),
                Map.entry("value", "v"), Map.entry("content", "c"),
                Map.entry("label", "l"), Map.entry("key", "k"),
                Map.entry("line", 1), Map.entry("col", 1),
                Map.entry("from", Map.of("line", 1, "col", 0)),
                Map.entry("to", Map.of("line", 1, "col", 1)),
                Map.entry("state", Map.of("a", "b"))));
        assertThat(result).isInstanceOf(Result.Success.class);
    }

    private Result execute(String actionName, Map<String, Object> params) {
        InvokeBinding.Aria binding = new InvokeBinding.Aria(actionName);
        Declaration decl = new Declaration(actionName, null, Map.of(), Map.of(), binding);
        Action action = handler.create(decl, binding);
        return action.execute(params, services);
    }

    private String line() {
        return out.toString(StandardCharsets.UTF_8).trim();
    }
}
