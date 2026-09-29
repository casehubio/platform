package io.casehub.yaml.step;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.Action;

public interface InvokeHandler {

    boolean supports(InvokeBinding binding);

    Action create(StepDefinition definition, InvokeBinding binding);
}
