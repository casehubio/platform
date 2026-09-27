package io.casehub.yaml.step.catalog;

import io.casehub.yaml.step.CatalogEntry;

import java.util.List;
import java.util.Map;

public sealed interface ResolvedStep permits
                                     ResolvedStep.PluginStep,
                                     ResolvedStep.InvokeStep,
                                     ResolvedStep.BlockStep,
                                     ResolvedStep.IfElseStep,
                                     ResolvedStep.MatchStep,
                                     ResolvedStep.ParallelStep,
                                     ResolvedStep.TryCatchFinallyStep {

    Map<String, Object> decorators();

    record PluginStep(
            CatalogEntry entry,
            Map<String, Object> params,
            Map<String, Object> decorators) implements ResolvedStep {

        public PluginStep {
            params     = Map.copyOf(params);
            decorators = Map.copyOf(decorators);
        }
    }

    record InvokeStep(
            Map<String, Object> invokeSpec,
            Map<String, Object> decorators) implements ResolvedStep {

        public InvokeStep {
            invokeSpec = Map.copyOf(invokeSpec);
            decorators = Map.copyOf(decorators);
        }
    }

    record BlockStep(
            List<ResolvedStep> steps,
            Map<String, Object> decorators) implements ResolvedStep {

        public BlockStep {
            steps      = List.copyOf(steps);
            decorators = Map.copyOf(decorators);
        }
    }

    record IfElseStep(
            String condition,
            List<ResolvedStep> thenSteps,
            List<ResolvedStep> elseSteps,
            Map<String, Object> decorators) implements ResolvedStep {

        public IfElseStep {
            thenSteps  = List.copyOf(thenSteps);
            elseSteps  = elseSteps != null ? List.copyOf(elseSteps) : List.of();
            decorators = Map.copyOf(decorators);
        }
    }

    record MatchStep(
            String scrutinee,
            List<ResolvedMatchCase> cases,
            Map<String, Object> decorators) implements ResolvedStep {

        public MatchStep {
            cases      = List.copyOf(cases);
            decorators = Map.copyOf(decorators);
        }
    }

    record ParallelStep(
            List<ResolvedStep> steps,
            Map<String, Object> decorators) implements ResolvedStep {

        public ParallelStep {
            steps      = List.copyOf(steps);
            decorators = Map.copyOf(decorators);
        }
    }

    record TryCatchFinallyStep(
            List<ResolvedStep> trySteps,
            List<ResolvedStep> catchSteps,
            List<ResolvedStep> finallySteps,
            Map<String, Object> decorators) implements ResolvedStep {

        public TryCatchFinallyStep {
            trySteps     = List.copyOf(trySteps);
            catchSteps   = catchSteps != null ? List.copyOf(catchSteps) : List.of();
            finallySteps = finallySteps != null ? List.copyOf(finallySteps) : List.of();
            decorators   = Map.copyOf(decorators);
        }
    }
}
