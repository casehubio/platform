package io.casehub.template.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryTemplateRegistry implements UnifiedTemplateRegistry {

    private final AtomicReference<Snapshot> snapshot =
        new AtomicReference<>(Snapshot.EMPTY);

    @Override
    public void register(TemplateNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        while (true) {
            Snapshot current = snapshot.get();
            if (current.namespaces.containsKey(namespace.name())) {
                throw new IllegalArgumentException(
                    "Namespace already registered: " + namespace.name());
            }
            Snapshot next = current.withNamespace(namespace);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    @Override
    public void deregister(String namespaceName) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        while (true) {
            Snapshot current = snapshot.get();
            Snapshot next = current.withoutNamespace(namespaceName);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    @Override
    public void replaceOrRegister(TemplateNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        while (true) {
            Snapshot current = snapshot.get();
            Snapshot next = current.withoutNamespace(namespace.name())
                                   .withNamespace(namespace);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    protected Snapshot currentSnapshot() {
        return snapshot.get();
    }

    protected boolean compareAndSetSnapshot(Snapshot expected, Snapshot next) {
        return snapshot.compareAndSet(expected, next);
    }


    @Override
    public TemplateMatch match(String input) {
        Objects.requireNonNull(input, "input");
        Snapshot snap = snapshot.get();
        for (var nsEntry : snap.namespaces.entrySet()) {
            TemplateMatch result = matchInNamespace(
                nsEntry.getKey(), nsEntry.getValue(), input, snap);
            if (result instanceof TemplateMatch.Matched) {
                return result;
            }
        }
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    public TemplateMatch match(String namespaceName, String input) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Objects.requireNonNull(input, "input");
        Snapshot snap = snapshot.get();
        TemplateNamespace ns = snap.namespaces.get(namespaceName);
        if (ns == null) {
            throw new IllegalArgumentException(
                "Unknown namespace: " + namespaceName);
        }
        return matchInNamespace(namespaceName, ns, input, snap);
    }

    protected TemplateMatch matchInNamespace(String namespaceName,
                                              TemplateNamespace ns,
                                              String input,
                                              Snapshot snap) {
        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition def = templateEntry.getValue();
            List<CompiledTemplate> compiledList =
                snap.compiled.get(namespaceName + ":" + def.name());
            if (compiledList == null) continue;

            for (int i = 0; i < compiledList.size(); i++) {
                CompiledTemplate compiled = compiledList.get(i);
                Optional<Map<String, String>> result =
                    compiled.extract(input, def.variables());
                if (result.isPresent()) {
                    return new TemplateMatch.Matched(
                        namespaceName, def.name(), i,
                        result.get(), 1.0);
                }
            }
        }
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    public MatchTraceResult matchWithTrace(String input) {
        Objects.requireNonNull(input, "input");
        Snapshot             snap          = snapshot.get();
        List<CandidateTrace> allCandidates = new ArrayList<>();
        TemplateMatch        matchResult   = new TemplateMatch.Unmatched(input);
        for (var nsEntry : snap.namespaces.entrySet()) {
            List<CandidateTrace> nsCandidates =
                    matchInNamespaceWithTrace(
                            nsEntry.getKey(), nsEntry.getValue(), input, snap);
            allCandidates.addAll(nsCandidates);
            if (matchResult instanceof TemplateMatch.Unmatched) {
                TemplateMatch nsResult = matchInNamespace(
                        nsEntry.getKey(), nsEntry.getValue(), input, snap);
                if (nsResult instanceof TemplateMatch.Matched) {
                    matchResult = nsResult;
                }
            }
        }
        return buildTraceResult(matchResult, allCandidates);
    }

    @Override
    public MatchTraceResult matchWithTrace(String namespaceName, String input) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Objects.requireNonNull(input, "input");
        Snapshot          snap = snapshot.get();
        TemplateNamespace ns   = snap.namespaces.get(namespaceName);
        if (ns == null) {
            throw new IllegalArgumentException(
                    "Unknown namespace: " + namespaceName);
        }
        List<CandidateTrace> candidates =
                matchInNamespaceWithTrace(namespaceName, ns, input, snap);
        TemplateMatch result = matchInNamespace(namespaceName, ns, input, snap);
        return buildTraceResult(result, candidates);
    }

    protected List<CandidateTrace> matchInNamespaceWithTrace(
            String namespaceName, TemplateNamespace ns,
            String input, Snapshot snap) {
        List<CandidateTrace> candidates = new ArrayList<>();
        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition def = templateEntry.getValue();
            List<CompiledTemplate> compiledList =
                    snap.compiled.get(namespaceName + ":" + def.name());
            if (compiledList == null) {continue;}

            for (int i = 0; i < compiledList.size(); i++) {
                CompiledTemplate compiled = compiledList.get(i);
                Optional<Map<String, String>> result =
                        compiled.extract(input, def.variables());
                if (result.isPresent()) {
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, -1.0,
                            CandidateOutcome.MATCHED, List.of(), 1.0));
                } else {
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, -1.0,
                            CandidateOutcome.REGEX_FAILED, List.of(), 0.0));
                }
            }
        }
        return candidates;
    }

    private MatchTraceResult buildTraceResult(TemplateMatch result,
                                              List<CandidateTrace> candidates) {
        String bestCandidate = null;
        double bestScore     = 0.0;
        for (CandidateTrace c : candidates) {
            if (c.outcome() == CandidateOutcome.MATCHED && c.score() > bestScore) {
                bestScore     = c.score();
                bestCandidate = c.namespace() + ":" + c.templateName();
            }
        }
        return new MatchTraceResult(result,
                                    new MatchTrace(candidates, bestCandidate, bestScore));
    }


    @Override
    public String generate(String namespaceName, String templateName,
                            Map<String, String> values) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(values, "values");

        Snapshot snap = snapshot.get();
        TemplateNamespace ns = snap.namespaces.get(namespaceName);
        if (ns == null) {
            throw new IllegalArgumentException(
                "Unknown namespace: " + namespaceName);
        }
        TemplateDefinition def = ns.templates().get(templateName);
        if (def == null) {
            throw new IllegalArgumentException(
                "Unknown template: " + templateName
                + " in namespace " + namespaceName);
        }
        return TemplateGenerator.generate(def, values);
    }

    @Override
    public Optional<TemplateSchema> schema(String namespaceName,
                                            String templateName) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Objects.requireNonNull(templateName, "templateName");
        Snapshot snap = snapshot.get();
        TemplateNamespace ns = snap.namespaces.get(namespaceName);
        if (ns == null) return Optional.empty();
        TemplateDefinition def = ns.templates().get(templateName);
        if (def == null) return Optional.empty();
        return Optional.of(buildSchema(namespaceName, def));
    }

    @Override
    public List<TemplateSchema> schemas(String namespaceName) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Snapshot snap = snapshot.get();
        TemplateNamespace ns = snap.namespaces.get(namespaceName);
        if (ns == null) return List.of();
        List<TemplateSchema> result = new ArrayList<>();
        for (TemplateDefinition def : ns.templates().values()) {
            result.add(buildSchema(namespaceName, def));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Set<String> namespaces() {
        return Collections.unmodifiableSet(
            new LinkedHashSet<>(snapshot.get().namespaces.keySet()));
    }

    protected TemplateSchema buildSchema(String namespaceName,
                                          TemplateDefinition def) {
        return new TemplateSchema(namespaceName, def.name(),
            def.patterns(), def.variables(), List.of());
    }

    protected static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

        final Map<String, TemplateNamespace> namespaces;
        final Map<String, List<CompiledTemplate>> compiled;

        Snapshot(Map<String, TemplateNamespace> namespaces,
                 Map<String, List<CompiledTemplate>> compiled) {
            this.namespaces = namespaces;
            this.compiled = compiled;
        }

        Snapshot withNamespace(TemplateNamespace ns) {
            var newNamespaces = new LinkedHashMap<>(namespaces);
            newNamespaces.put(ns.name(), ns);
            var newCompiled = new LinkedHashMap<>(compiled);
            for (TemplateDefinition def : ns.templates().values()) {
                List<CompiledTemplate> templates = new ArrayList<>();
                for (String pattern : def.patterns()) {
                    templates.add(RegexTemplateCompiler.compile(pattern));
                }
                newCompiled.put(ns.name() + ":" + def.name(),
                    Collections.unmodifiableList(templates));
            }
            return new Snapshot(
                Collections.unmodifiableMap(newNamespaces),
                Collections.unmodifiableMap(newCompiled));
        }

        Snapshot withoutNamespace(String name) {
            var newNamespaces = new LinkedHashMap<>(namespaces);
            TemplateNamespace removed = newNamespaces.remove(name);
            var newCompiled = new LinkedHashMap<>(compiled);
            if (removed != null) {
                for (String templateName : removed.templates().keySet()) {
                    newCompiled.remove(name + ":" + templateName);
                }
            }
            return new Snapshot(
                Collections.unmodifiableMap(newNamespaces),
                Collections.unmodifiableMap(newCompiled));
        }
    }
}
