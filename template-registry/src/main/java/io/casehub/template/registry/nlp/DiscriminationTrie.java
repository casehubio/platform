package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.ElementKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiscriminationTrie {

    public record PatternRef(String templateName, int patternIndex) {}

    static final class TrieNode {
        final Map<String, TrieNode> literalChildren;
        final TrieNode wildcardChild;
        final List<PatternRef> leaves;

        TrieNode(Map<String, TrieNode> literalChildren, TrieNode wildcardChild,
                 List<PatternRef> leaves) {
            this.literalChildren = Map.copyOf(literalChildren);
            this.wildcardChild = wildcardChild;
            this.leaves = List.copyOf(leaves);
        }
    }

    private final TrieNode root;

    private DiscriminationTrie(TrieNode root) {
        this.root = root;
    }

    public static DiscriminationTrie build(
            Map<String, NlpTemplateCompiler.CompiledNlpTemplate> compiledTemplates,
            String namespaceName) {
        MutableNode mutableRoot = new MutableNode();
        String prefix = namespaceName + ":";

        for (var entry : compiledTemplates.entrySet()) {
            String key = entry.getKey();
            String templateName = key.startsWith(prefix)
                    ? key.substring(prefix.length()) : key;
            NlpTemplateCompiler.CompiledNlpTemplate compiled = entry.getValue();

            for (int i = 0; i < compiled.matchers().size(); i++) {
                List<StructuralMatcher.Element> elements = compiled.matchers().get(i).elements();
                MutableNode current = mutableRoot;

                for (StructuralMatcher.Element elem : elements) {
                    if (elem.kind() == ElementKind.LITERAL) {
                        current = current.literalChildren.computeIfAbsent(
                                elem.lemma().toLowerCase(), k -> new MutableNode());
                    } else {
                        if (current.wildcardChild == null) {
                            current.wildcardChild = new MutableNode();
                        }
                        current = current.wildcardChild;
                    }
                }
                current.leaves.add(new PatternRef(templateName, i));
            }
        }

        return new DiscriminationTrie(mutableRoot.freeze());
    }

    public List<PatternRef> walk(List<AnnotatedToken> inputTokens) {
        List<PatternRef> results = new ArrayList<>();
        walkRecursive(root, inputTokens, 0, results);
        return results;
    }

    private void walkRecursive(TrieNode node, List<AnnotatedToken> tokens,
                                int tokenIndex, List<PatternRef> results) {
        results.addAll(node.leaves);

        if (tokenIndex >= tokens.size()) return;

        String lemma = tokens.get(tokenIndex).lemma().toLowerCase();

        TrieNode literalChild = node.literalChildren.get(lemma);
        if (literalChild != null) {
            walkRecursive(literalChild, tokens, tokenIndex + 1, results);
        }

        if (node.wildcardChild != null) {
            for (int t = tokenIndex; t < tokens.size(); t++) {
                String scanLemma = tokens.get(t).lemma().toLowerCase();
                TrieNode nextLiteral = node.wildcardChild.literalChildren.get(scanLemma);
                if (nextLiteral != null) {
                    walkRecursive(nextLiteral, tokens, t + 1, results);
                }
            }
            results.addAll(node.wildcardChild.leaves);
        }
    }

    private static final class MutableNode {
        final Map<String, MutableNode> literalChildren = new HashMap<>();
        MutableNode wildcardChild;
        final List<PatternRef> leaves = new ArrayList<>();

        TrieNode freeze() {
            Map<String, TrieNode> frozenLiterals = new HashMap<>();
            literalChildren.forEach((k, v) -> frozenLiterals.put(k, v.freeze()));
            return new TrieNode(
                    frozenLiterals,
                    wildcardChild != null ? wildcardChild.freeze() : null,
                    leaves);
        }
    }
}
