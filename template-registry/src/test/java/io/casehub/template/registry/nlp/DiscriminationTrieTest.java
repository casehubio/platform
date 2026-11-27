package io.casehub.template.registry.nlp;

import io.casehub.template.registry.TemplateDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscriminationTrieTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void singleLiteralPattern_matchesExactInput() {
        var trie = buildTrie(Map.of(
                "cancel-order", def("cancel-order", "Cancel ${item}")));

        var results = trie.walk(tokenizer.tokenize("Cancel my subscription"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).templateName()).isEqualTo("cancel-order");
        assertThat(results.get(0).patternIndex()).isEqualTo(0);
    }

    @Test
    void singleLiteralPattern_noMatchForDifferentInput() {
        var trie = buildTrie(Map.of(
                "cancel-order", def("cancel-order", "Cancel ${item}")));

        var results = trie.walk(tokenizer.tokenize("Submit the report"));
        assertThat(results).isEmpty();
    }

    @Test
    void twoTemplates_discriminatesOnFirstLiteral() {
        var trie = buildTrie(Map.of(
                "cancel-order", def("cancel-order", "Cancel ${item}"),
                "submit-report", def("submit-report", "Submit ${item}")));

        var cancelResults = trie.walk(tokenizer.tokenize("Cancel my subscription"));
        assertThat(cancelResults).hasSize(1);
        assertThat(cancelResults.get(0).templateName()).isEqualTo("cancel-order");

        var submitResults = trie.walk(tokenizer.tokenize("Submit the report"));
        assertThat(submitResults).hasSize(1);
        assertThat(submitResults.get(0).templateName()).isEqualTo("submit-report");
    }

    @Test
    void multiLiteralPattern_matchesPositionally() {
        var trie = buildTrie(Map.of(
                "cancel-now", def("cancel-now", "Cancel ${item} now")));

        var withNow = trie.walk(tokenizer.tokenize("Cancel my subscription now"));
        assertThat(withNow).hasSize(1);

        var withoutNow = trie.walk(tokenizer.tokenize("Cancel my subscription"));
        assertThat(withoutNow).isEmpty();
    }

    @Test
    void leadingVariable_usesWildcardRoot() {
        var trie = buildTrie(Map.of(
                "action", def("action", "${action} the ${target}")));

        var results = trie.walk(tokenizer.tokenize("Handle the report"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).templateName()).isEqualTo("action");
    }

    @Test
    void sharedPrefix_branchesCorrectly() {
        var trie = buildTrie(Map.of(
                "cancel-now", def("cancel-now", "Cancel ${item} now"),
                "cancel-later", def("cancel-later", "Cancel ${item} later")));

        var nowResults = trie.walk(tokenizer.tokenize("Cancel my order now"));
        assertThat(nowResults).extracting(DiscriminationTrie.PatternRef::templateName)
                .containsExactly("cancel-now");

        var laterResults = trie.walk(tokenizer.tokenize("Cancel my order later"));
        assertThat(laterResults).extracting(DiscriminationTrie.PatternRef::templateName)
                .containsExactly("cancel-later");
    }

    @Test
    void multiplePatterns_sameTemplate() {
        var trie = buildTrie(Map.of(
                "greeting", new TemplateDefinition("greeting",
                        List.of("Hello ${name}", "Hi ${name}"), Map.of())));

        var helloResults = trie.walk(tokenizer.tokenize("Hello world"));
        assertThat(helloResults).hasSize(1);
        assertThat(helloResults.get(0).patternIndex()).isEqualTo(0);

        var hiResults = trie.walk(tokenizer.tokenize("Hi world"));
        assertThat(hiResults).hasSize(1);
        assertThat(hiResults.get(0).patternIndex()).isEqualTo(1);
    }

    @Test
    void emptyTrie_returnsEmpty() {
        var trie = buildTrie(Map.of());
        var results = trie.walk(tokenizer.tokenize("anything"));
        assertThat(results).isEmpty();
    }

    @Test
    void wildcardScanAhead_findsLiteralAfterMultipleTokens() {
        var trie = buildTrie(Map.of(
                "action", def("action",
                        "${action} the ${target} at ${location} promptly")));

        var results = trie.walk(tokenizer.tokenize(
                "Handle the quarterly report at headquarters promptly"));
        assertThat(results).hasSize(1);
    }

    private DiscriminationTrie buildTrie(Map<String, TemplateDefinition> templates) {
        Map<String, NlpTemplateCompiler.CompiledNlpTemplate> compiled = new LinkedHashMap<>();
        for (var entry : templates.entrySet()) {
            compiled.put("ns:" + entry.getKey(),
                    NlpTemplateCompiler.compile(entry.getValue(), tokenizer));
        }
        return DiscriminationTrie.build(compiled, "ns");
    }

    private TemplateDefinition def(String name, String pattern) {
        return new TemplateDefinition(name, List.of(pattern), Map.of());
    }
}
