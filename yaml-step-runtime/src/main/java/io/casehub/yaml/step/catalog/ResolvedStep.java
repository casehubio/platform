package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.annotation.Annotation;
import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.plugin.api.StreamingStepFactory;
import java.util.List;
import java.util.Map;

public sealed interface ResolvedStep permits
                                     ResolvedStep.PluginStep,
                                     ResolvedStep.InvokeStep,
                                     ResolvedStep.BlockStep,
                                     ResolvedStep.IfElseStep,
                                     ResolvedStep.MatchStep,
                                     ResolvedStep.ParallelStep,
                                     ResolvedStep.TryCatchFinallyStep,
                                     ResolvedStep.SelectStep,
                                     ResolvedStep.BarrierStep,
                                     ResolvedStep.QuorumStep,
                                     ResolvedStep.ControlStep,
                                     ResolvedStep.RaceStep,
                                     ResolvedStep.FusedPipelineStep,
                                     ResolvedStep.ErrorStep {

    Map<String, Object> decorators();

    default String name()                  {return null;}

    default List<Annotation> annotations() {return List.of();}


    record PluginStep(
            String name,
            String actionName,
            Map<String, Object> params,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public PluginStep {
            params      = Map.copyOf(params);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public PluginStep(String name, String actionName, Map<String, Object> params, Map<String, Object> decorators) {
            this(name, actionName, params, decorators, List.of());
        }
    }

    record InvokeStep(
            String name,
            Map<String, Object> invokeSpec,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public InvokeStep {
            invokeSpec  = Map.copyOf(invokeSpec);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public InvokeStep(String name, Map<String, Object> invokeSpec, Map<String, Object> decorators) {
            this(name, invokeSpec, decorators, List.of());
        }
    }

    record BlockStep(
            String name,
            List<ResolvedStep> steps,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public BlockStep {
            steps       = List.copyOf(steps);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public BlockStep(String name, List<ResolvedStep> steps, Map<String, Object> decorators) {
            this(name, steps, decorators, List.of());
        }
    }

    record IfElseStep(
            String name,
            String condition,
            List<ResolvedStep> thenSteps,
            List<ResolvedStep> elseSteps,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public IfElseStep {
            thenSteps   = List.copyOf(thenSteps);
            elseSteps   = elseSteps != null ? List.copyOf(elseSteps) : List.of();
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public IfElseStep(String name, String condition, List<ResolvedStep> thenSteps, List<ResolvedStep> elseSteps, Map<String, Object> decorators) {
            this(name, condition, thenSteps, elseSteps, decorators, List.of());
        }
    }

    record MatchStep(
            String name,
            String scrutinee,
            List<ResolvedMatchCase> cases,
            TemplateMatchSpec templateSpec,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public MatchStep {
            cases       = cases != null ? List.copyOf(cases) : List.of();
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public boolean isTemplateMode() { return templateSpec != null; }

        static MatchStep valueMode(String name, String scrutinee,
                List<ResolvedMatchCase> cases, Map<String, Object> decorators) {
            java.util.Objects.requireNonNull(scrutinee, "scrutinee required for value mode");
            return new MatchStep(name, scrutinee, cases, null, decorators, List.of());
        }

        static MatchStep valueMode(String name, String scrutinee,
                List<ResolvedMatchCase> cases, Map<String, Object> decorators,
                List<Annotation> annotations) {
            java.util.Objects.requireNonNull(scrutinee, "scrutinee required for value mode");
            return new MatchStep(name, scrutinee, cases, null, decorators, annotations);
        }

        static MatchStep templateMode(String name, TemplateMatchSpec templateSpec,
                Map<String, Object> decorators) {
            java.util.Objects.requireNonNull(templateSpec, "templateSpec required for template mode");
            return new MatchStep(name, null, List.of(), templateSpec, decorators, List.of());
        }

        static MatchStep templateMode(String name, TemplateMatchSpec templateSpec,
                Map<String, Object> decorators, List<Annotation> annotations) {
            java.util.Objects.requireNonNull(templateSpec, "templateSpec required for template mode");
            return new MatchStep(name, null, List.of(), templateSpec, decorators, annotations);
        }

        public MatchStep(String name, String scrutinee, List<ResolvedMatchCase> cases, Map<String, Object> decorators) {
            this(name, scrutinee, cases, null, decorators, List.of());
        }

        public MatchStep(String name, String scrutinee, List<ResolvedMatchCase> cases,
                Map<String, Object> decorators, List<Annotation> annotations) {
            this(name, scrutinee, cases, null, decorators, annotations);
        }
    }

    record ParallelStep(
            String name,
            List<ResolvedStep> steps,
            int advanceAfter,
            boolean cancelRemaining,
            java.time.Duration collectionTimeout,
            int concurrency,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public ParallelStep {
            steps       = List.copyOf(steps);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
            if (!steps.isEmpty()) {
                if (advanceAfter <= 0) {
                    throw new IllegalArgumentException("advance-after must be > 0, got: " + advanceAfter);
                }
                if (advanceAfter > steps.size()) {
                    throw new IllegalArgumentException(
                            "advance-after (" + advanceAfter + ") > steps (" + steps.size() + ")");
                }
            }
            if (concurrency < 0) {
                throw new IllegalArgumentException("concurrency must be >= 0, got: " + concurrency);
            }
        }

        public ParallelStep(String name, List<ResolvedStep> steps, Map<String, Object> decorators) {
            this(name, steps, steps.isEmpty() ? 1 : steps.size(), false, null, 0, decorators, List.of());
        }

        public ParallelStep(String name, List<ResolvedStep> steps, int advanceAfter, boolean cancelRemaining, java.time.Duration collectionTimeout, Map<String, Object> decorators) {
            this(name, steps, advanceAfter, cancelRemaining, collectionTimeout, 0, decorators, List.of());
        }

        public ParallelStep(String name, List<ResolvedStep> steps, int advanceAfter, boolean cancelRemaining, java.time.Duration collectionTimeout, Map<String, Object> decorators, List<Annotation> annotations) {
            this(name, steps, advanceAfter, cancelRemaining, collectionTimeout, 0, decorators, annotations);
        }

        public ParallelStep(String name, List<ResolvedStep> steps, int advanceAfter, boolean cancelRemaining, java.time.Duration collectionTimeout, int concurrency, Map<String, Object> decorators) {
            this(name, steps, advanceAfter, cancelRemaining, collectionTimeout, concurrency, decorators, List.of());
        }
    }

    record TryCatchFinallyStep(
            String name,
            List<ResolvedStep> trySteps,
            List<ResolvedStep> catchSteps,
            List<ResolvedStep> finallySteps,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public TryCatchFinallyStep {
            trySteps     = List.copyOf(trySteps);
            catchSteps   = catchSteps != null ? List.copyOf(catchSteps) : List.of();
            finallySteps = finallySteps != null ? List.copyOf(finallySteps) : List.of();
            decorators   = Map.copyOf(decorators);
            annotations  = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public TryCatchFinallyStep(String name, List<ResolvedStep> trySteps, List<ResolvedStep> catchSteps, List<ResolvedStep> finallySteps, Map<String, Object> decorators) {
            this(name, trySteps, catchSteps, finallySteps, decorators, List.of());
        }
    }

    enum SelectBranchType {SUBSCRIBE, WAIT}

    record SelectBranch(
            SelectBranchType type,
            String name,
            List<ResolvedStep> steps) {

        public SelectBranch {
            if (type == null) {
                throw new IllegalArgumentException("SelectBranch type must not be null");
            }
            steps = List.copyOf(steps);
        }
    }

    record SelectStep(
            String name,
            List<SelectBranch> branches,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public SelectStep {
            branches    = List.copyOf(branches);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public SelectStep(String name, List<SelectBranch> branches, Map<String, Object> decorators) {
            this(name, branches, decorators, List.of());
        }
    }

    record BarrierStep(
            String name,
            List<String> awaitSteps,
            java.time.Duration timeout,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public BarrierStep {
            if (awaitSteps == null || awaitSteps.isEmpty()) {
                throw new IllegalArgumentException("barrier 'await' must be non-empty");
            }
            awaitSteps  = List.copyOf(awaitSteps);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public BarrierStep(String name, List<String> awaitSteps, java.time.Duration timeout, Map<String, Object> decorators) {
            this(name, awaitSteps, timeout, decorators, List.of());
        }
    }

    record QuorumStep(
            String name,
            int required,
            List<String> ofSteps,
            java.time.Duration timeout,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public QuorumStep {
            if (required <= 0) {throw new IllegalArgumentException("quorum 'required' must be > 0");}
            if (required > ofSteps.size()) {
                throw new IllegalArgumentException(
                        "quorum 'required' (" + required + ") > 'of' (" + ofSteps.size() + ")");
            }
            ofSteps     = List.copyOf(ofSteps);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public QuorumStep(String name, int required, List<String> ofSteps, java.time.Duration timeout, Map<String, Object> decorators) {
            this(name, required, ofSteps, timeout, decorators, List.of());
        }
    }

    record ControlStep(
            String name,
            String target,
            boolean activate,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public ControlStep {
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException(
                        "pause/resume requires a non-blank target consumer name");
            }
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public ControlStep(String name, String target, boolean activate, Map<String, Object> decorators) {
            this(name, target, activate, decorators, List.of());
        }
    }

    record RaceStep(
            String name,
            List<ResolvedStep> branches,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public RaceStep {
            if (branches == null || branches.size() < 2) {
                throw new IllegalArgumentException("race requires at least 2 branches");
            }
            branches    = List.copyOf(branches);
            decorators  = Map.copyOf(decorators);
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public RaceStep(String name, List<ResolvedStep> branches, Map<String, Object> decorators) {
            this(name, branches, decorators, List.of());
        }
    }

    record FusedStageDescriptor(
            String stepName,
            String actionName,
            Map<String, Object> params,
            Map<String, Object> decorators,
            String inputKey,
            StreamingStepFactory<?, ?> factory
    ) {
        public FusedStageDescriptor {
            params     = Map.copyOf(params);
            decorators = Map.copyOf(decorators);
        }
    }

    record FusedPipelineStep(
            String name,
            List<FusedStageDescriptor> stages,
            Map<String, Object> decorators) implements ResolvedStep {
        public FusedPipelineStep {
            stages     = List.copyOf(stages);
            decorators = Map.copyOf(decorators);
        }
    }

    record ErrorStep(
            String name,
            Map<String, Object> originalYaml,
            ParseError error,
            Map<String, Object> decorators,
            List<Annotation> annotations) implements ResolvedStep {

        public ErrorStep {
            originalYaml = originalYaml != null ? Map.copyOf(originalYaml) : Map.of();
            decorators   = Map.copyOf(decorators);
            annotations  = annotations != null ? List.copyOf(annotations) : List.of();
        }

        public ErrorStep(String name, Map<String, Object> originalYaml, ParseError error) {
            this(name, originalYaml, error, Map.of(), List.of());
        }
    }
}
