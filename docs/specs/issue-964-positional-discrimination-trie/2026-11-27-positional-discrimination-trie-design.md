# Positional Discrimination Trie — Design Spec

**Issue:** #964 — perf: positional discrimination trie for template candidate selection
**Epic:** #943 (unified template registry)
**Scale:** S | **Complexity:** Med

## Goal

Replace the O(T×P) bag-of-words keyword affinity scan with a structurally-aware trie for NLP candidate selection when template counts per namespace exceed a configurable threshold. The trie uses template literal lemmas at their structural positions (interleaved with wildcard nodes for variable slots) to narrow candidates to O(depth) lookup, then delegates to `StructuralMatcher` for precise matching.

## Architecture

### Current Flow (per namespace)

`NlpTemplateRegistry.matchInNamespace()` iterates every template → every pattern:

1. Look up `nlpCompiled.get(namespaceName + ":" + def.name())` → `CompiledNlpTemplate`
2. For each pattern index `i`: compute `LemmatisedKeywordFilter.affinity(keywords, inputTokens)`
3. If affinity < `affinityThreshold` (0.3) and keywords non-empty → skip
4. Otherwise → `compiled.matchers().get(i).match(inputTokens, variables)`

This is O(T×P) where T = templates, P = patterns per template. At 100+ templates with 2-3 patterns each, this means 200-300 affinity checks per input.

### Proposed Flow

When a namespace's total pattern count exceeds the threshold (default 100), `matchInNamespace()` switches to trie-based candidate selection:

1. Walk the `DiscriminationTrie` with `inputTokens` → returns `List<PatternRef>` (candidate patterns whose positional structure aligns with the input)
2. For each candidate → `compiled.matchers().get(ref.patternIndex()).match(inputTokens, variables)`

The bag-of-words affinity loop remains unchanged below the threshold.

### Trie Data Structure

```
DiscriminationTrie
├── LiteralNode("cancel")
│   ├── WildcardNode
│   │   └── LiteralNode("now") → [PatternRef("ns:cancel-order:0")]
│   └── LiteralNode("now")
│       └── WildcardNode → [PatternRef("ns:cancel-immediate:0")]
├── LiteralNode("submit")
│   └── WildcardNode
│       └── LiteralNode("review") → [PatternRef("ns:submit-review:0")]
└── WildcardNode (templates starting with ${var})
    └── LiteralNode("the")
        └── WildcardNode
            └── LiteralNode("promptly") → [PatternRef("ns:action:0")]
```

**Node types:**

- `LiteralNode` — keyed by lemma string. Children stored in `Map<String, TrieNode>`. Matches when the current input token's lemma equals the node's key.
- `WildcardNode` — represents a CLAUSE or VARIABLE template element. Consumes one or more input tokens. Children are `LiteralNode`s. During walk, scans remaining input tokens for any child's lemma key (scan-ahead, shortest skip first).
- Both node types can carry `List<PatternRef>` leaf entries (patterns that terminate at this depth).

**`PatternRef` record:** `(String namespaceName, String templateName, int patternIndex)` — identifies the pattern for structural matcher validation.

**Construction:** `DiscriminationTrie.build(Map<String, CompiledNlpTemplate> compiledTemplates, String namespaceName)` — iterates all patterns, extracts `StructuralMatcher.Element` sequences via `matcher.elements()`, builds the trie by walking elements in order:
- `ElementKind.LITERAL` → `LiteralNode(element.lemma())`
- `ElementKind.CLAUSE` or `ElementKind.VARIABLE` → `WildcardNode`
- Adjacent wildcards (variable followed by variable) are invalid per `StructuralMatcher.validateNoAdjacentClauses()` — no need to handle
- Optional elements: included in the trie path but the walk handles them (see below)

**Immutability:** The trie is immutable after construction. Rebuilt on `register()`, `deregister()`, `replaceOrRegister()`.

### Trie Walk Algorithm

```
walk(inputTokens, node, tokenIndex) → List<PatternRef>:
  results = []
  if node has leaf entries:
    results.addAll(node.leafEntries)

  if tokenIndex >= inputTokens.size():
    return results

  if node is LiteralNode with children:
    token = inputTokens[tokenIndex]
    child = children.get(token.lemma())
    if child != null:
      results.addAll(walk(inputTokens, child, tokenIndex + 1))

  if node is WildcardNode with children:
    // Scan-ahead: find children's literal lemmas in remaining tokens
    for t = tokenIndex to inputTokens.size() - 1:
      child = children.get(inputTokens[t].lemma())
      if child != null:
        results.addAll(walk(inputTokens, child, t + 1))
        // Continue scanning — multiple children may match at different positions

  return results
```

The walk is recursive depth-first. At wildcard nodes, it scans ahead for any child literal, branching when multiple match. Branching factor is bounded by distinct next-literals across patterns sharing a prefix (typically 2-5).

**Optional elements:** Optional literal elements in the template are included in the trie. During the walk, if a literal child doesn't match, the walk also tries skipping (treating it as unmatched optional). This mirrors `StructuralMatcher`'s optional handling.

### Integration into NlpTemplateRegistry

**New field:**
```java
private final ConcurrentMap<String, DiscriminationTrie> namespaceTries = new ConcurrentHashMap<>();
private final int trieActivationThreshold;
```

Default `trieActivationThreshold`: 100 (total patterns across all templates in a namespace). Configurable via constructor parameter with a default.

**matchInNamespace() modification:**
```java
protected TemplateMatch matchInNamespace(String namespaceName, TemplateNamespace ns,
                                          String input, Snapshot snap) {
    TemplateMatch regexResult = super.matchInNamespace(namespaceName, ns, input, snap);
    if (regexResult instanceof TemplateMatch.Matched) return regexResult;

    List<AnnotatedToken> inputTokens = tokenizer.tokenize(input);

    DiscriminationTrie trie = namespaceTries.get(namespaceName);
    if (trie != null) {
        return matchViaTrie(namespaceName, ns, inputTokens, trie);
    }

    // existing bag-of-words affinity loop (unchanged)
    ...
}
```

The `matchViaTrie()` method walks the trie, then runs `StructuralMatcher.match()` on each candidate. Best match / near miss / unmatched logic is identical to the existing loop.

**Lifecycle methods:**

- `register()`: after `super.register()` and NLP compilation, check total pattern count. If >= threshold, build trie and store in `namespaceTries`.
- `deregister()`: remove from `namespaceTries`.
- `replaceOrRegister()`: remove old trie, check count, rebuild if needed.

### AdaptiveTemplateRegistry Compatibility

`AdaptiveTemplateRegistry` overrides `matchInNamespace()` with embedding rescue logic. When the trie is active in the parent, the adaptive override's call to `super.matchInNamespace()` already benefits — the parent uses trie-based candidate selection instead of the bag-of-words loop.

The embedding rescue code path in `AdaptiveTemplateRegistry.matchInNamespace()` currently replicates the bag-of-words loop. When the trie is active, this path is not reached (the parent's trie path handles candidate selection). No changes needed to `AdaptiveTemplateRegistry` for the trie to work.

### Thread Safety

- `DiscriminationTrie` is immutable after construction — safe for concurrent reads
- `namespaceTries` is a `ConcurrentHashMap` — atomic put/remove
- Trie construction happens in `register()`/`replaceOrRegister()` on the caller's thread, same as `nlpCompiled` population

### Performance Characteristics

| Metric | Bag-of-words (current) | Trie (proposed) |
|--------|----------------------|-----------------|
| Candidate selection | O(T×P×K) | O(D×B) where D=trie depth, B=branching at wildcards |
| Memory | O(T×P) keyword sets | O(T×P×E) trie nodes where E=elements per pattern |
| Build cost | O(T×P) per register | O(T×P×E) per register (trie construction) |
| Activation | Always | Only when patterns >= threshold |

At 200 patterns with average depth 4 and branching 3: trie walk is ~12 operations vs 200 affinity checks.

## Constraints

- `template-registry` module — no external dependencies
- `DiscriminationTrie` uses only `StructuralMatcher.Element` data already computed by `NlpTemplateCompiler`
- Thread safety via immutable trie + `ConcurrentHashMap` — no `synchronized` (virtual thread pinning)
- Existing bag-of-words path unchanged for small registries

## Testing Strategy

- Unit tests for `DiscriminationTrie`: construction from element sequences, walk with literal matching, walk with wildcard scan-ahead, walk with optional elements, empty trie, single-pattern trie
- Unit tests for threshold activation: below threshold uses bag-of-words, above threshold uses trie, boundary behavior
- Integration tests: same match results whether trie or bag-of-words is active (correctness equivalence)
- Performance benchmark: compare match latency at 50, 100, 200, 500 templates (not a gate, but informational)

## References

- `NlpTemplateRegistry.matchInNamespace()` — current candidate selection loop
- `StructuralMatcher.Element` — element structure the trie indexes on
- `StructuralMatcher.captureClause()` — scan-ahead semantics the trie walk mirrors
- `LemmatisedKeywordFilter.affinity()` — the bag-of-words scoring the trie replaces at scale
- `NlpTemplateCompiler.CompiledNlpTemplate` — parallel lists (matchers, keywordSets, summaries) per pattern
- Issue #964 — focal issue
- Issue #959 — unified template registry architecture
- Issue #982 — embedding rescue tier (independent, stays in AdaptiveTemplateRegistry)
- `specs/issue-964-positional-discrimination-trie/decisions.md` — 6 design decisions
