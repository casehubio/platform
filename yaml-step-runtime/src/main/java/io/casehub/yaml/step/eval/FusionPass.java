package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;
import io.casehub.yaml.step.catalog.ResolvedStep;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FusionPass {

    private static final Set<String> CHAIN_BREAKING_DECORATORS = Set.of(
        "from", "background", "when", "on-error", "wait", "at",
        "forEach", "cancel", "loop", "transition", "signal", "publish",
        "semaphore", "mutex", "transform");

    private static final Pattern RESULT_REF = Pattern.compile(
        "\\$\\{result\\.([a-zA-Z0-9_-]+)");

    private FusionPass() {}

    public static List<ResolvedStep> fuse(List<ResolvedStep> steps, PluginRegistry registry) {
        if (steps.size() < 2) return steps;

        Set<String> referencedNames = collectReferencedNames(steps);
        List<ResolvedStep> result = new ArrayList<>();

        List<ResolvedStep.PluginStep> candidateSteps = new ArrayList<>();
        List<StreamingTypeInfo> candidateTypes = new ArrayList<>();

        for (ResolvedStep step : steps) {
            if (step instanceof ResolvedStep.PluginStep plugin) {
                Optional<Definition> defOpt = registry.resolve(plugin.actionName());
                if (defOpt.isPresent() && defOpt.get().streamingType().isPresent()) {
                    StreamingTypeInfo typeInfo = defOpt.get().streamingType().get();

                    if (candidateSteps.isEmpty()) {
                        if (!hasChainBreakingDecorator(plugin)) {
                            candidateSteps.add(plugin);
                            candidateTypes.add(typeInfo);
                        } else {
                            result.add(step);
                        }
                    } else if (isTypeCompatible(candidateTypes.get(candidateTypes.size() - 1), typeInfo)
                               && !hasChainBreakingDecorator(plugin)
                               && !hasReferencedNonLastMember(referencedNames, candidateSteps)) {
                        candidateSteps.add(plugin);
                        candidateTypes.add(typeInfo);
                    } else {
                        flushCandidate(candidateSteps, candidateTypes, referencedNames, result);
                        if (!hasChainBreakingDecorator(plugin)) {
                            candidateSteps.add(plugin);
                            candidateTypes.add(typeInfo);
                        } else {
                            result.add(step);
                        }
                    }
                    continue;
                }
            }
            flushCandidate(candidateSteps, candidateTypes, referencedNames, result);
            result.add(step);
        }
        flushCandidate(candidateSteps, candidateTypes, referencedNames, result);
        return List.copyOf(result);
    }

    private static boolean isTypeCompatible(StreamingTypeInfo producer, StreamingTypeInfo consumer) {
        return consumer.inputType().isAssignableFrom(producer.outputType());
    }

    private static boolean hasChainBreakingDecorator(ResolvedStep.PluginStep step) {
        for (String key : step.decorators().keySet()) {
            if (CHAIN_BREAKING_DECORATORS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReferencedNonLastMember(Set<String> referencedNames,
                                                       List<ResolvedStep.PluginStep> currentChain) {
        for (ResolvedStep.PluginStep existing : currentChain) {
            if (existing.name() != null && referencedNames.contains(existing.name())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> collectReferencedNames(List<ResolvedStep> steps) {
        Set<String> refs = new HashSet<>();
        for (ResolvedStep step : steps) {
            collectRefsFromStep(step, refs);
        }
        return refs;
    }

    private static void collectRefsFromStep(ResolvedStep step, Set<String> refs) {
        switch (step) {
            case ResolvedStep.PluginStep p -> {
                scanMapForRefs(p.params(), refs);
                scanMapForRefs(p.decorators(), refs);
            }
            case ResolvedStep.BlockStep b -> {
                for (ResolvedStep sub : b.steps()) collectRefsFromStep(sub, refs);
            }
            case ResolvedStep.BarrierStep b -> refs.addAll(b.awaitSteps());
            case ResolvedStep.QuorumStep q -> refs.addAll(q.ofSteps());
            case ResolvedStep.ParallelStep p -> {
                for (ResolvedStep sub : p.steps()) collectRefsFromStep(sub, refs);
            }
            case ResolvedStep.IfElseStep i -> {
                for (ResolvedStep sub : i.thenSteps()) collectRefsFromStep(sub, refs);
                for (ResolvedStep sub : i.elseSteps()) collectRefsFromStep(sub, refs);
            }
            case ResolvedStep.TryCatchFinallyStep t -> {
                for (ResolvedStep sub : t.trySteps()) collectRefsFromStep(sub, refs);
                for (ResolvedStep sub : t.catchSteps()) collectRefsFromStep(sub, refs);
                for (ResolvedStep sub : t.finallySteps()) collectRefsFromStep(sub, refs);
            }
            case ResolvedStep.RaceStep r -> {
                for (ResolvedStep sub : r.branches()) collectRefsFromStep(sub, refs);
            }
            case ResolvedStep.InvokeStep inv -> {
                scanMapForRefs(inv.invokeSpec(), refs);
                scanMapForRefs(inv.decorators(), refs);
            }
            case ResolvedStep.MatchStep m -> {
                for (var c : m.cases()) {
                    for (ResolvedStep sub : c.steps()) collectRefsFromStep(sub, refs);
                }
            }
            case ResolvedStep.SelectStep sel -> {
                for (var branch : sel.branches()) {
                    for (ResolvedStep sub : branch.steps()) collectRefsFromStep(sub, refs);
                }
            }
            case ResolvedStep.FusedPipelineStep fps -> {
                for (var stage : fps.stages()) {
                    scanMapForRefs(stage.params(), refs);
                    scanMapForRefs(stage.decorators(), refs);
                }
            }
            case ResolvedStep.ControlStep ignored -> {}
            default -> {}
        }
    }

    private static void scanMapForRefs(Map<String, Object> map, Set<String> refs) {
        for (Object value : map.values()) {
            if (value instanceof String s) {
                Matcher m = RESULT_REF.matcher(s);
                while (m.find()) {
                    refs.add(m.group(1));
                }
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                scanMapForRefs(nestedMap, refs);
            }
        }
    }

    private static void flushCandidate(List<ResolvedStep.PluginStep> candidateSteps,
                                        List<StreamingTypeInfo> candidateTypes,
                                        Set<String> referencedNames,
                                        List<ResolvedStep> result) {
        if (candidateSteps.isEmpty()) return;

        if (candidateSteps.size() == 1) {
            result.add(candidateSteps.get(0));
        } else {
            List<ResolvedStep.FusedStageDescriptor> stages = new ArrayList<>();
            for (int i = 0; i < candidateSteps.size(); i++) {
                ResolvedStep.PluginStep ps = candidateSteps.get(i);
                stages.add(new ResolvedStep.FusedStageDescriptor(
                    ps.name(), ps.actionName(), ps.params(), ps.decorators(),
                    candidateTypes.get(i).inputKey(), candidateTypes.get(i).factory()));
            }
            String chainName = candidateSteps.get(candidateSteps.size() - 1).name();
            result.add(new ResolvedStep.FusedPipelineStep(chainName, stages, Map.of()));
        }
        candidateSteps.clear();
        candidateTypes.clear();
    }
}
