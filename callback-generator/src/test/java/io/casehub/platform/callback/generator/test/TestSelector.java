package io.casehub.platform.callback.generator.test;

import io.casehub.platform.api.mcp.CallbackEligible;

@CallbackEligible(name = "test-selector", fanOut = false)
public interface TestSelector {

    String selectWorker(String taskId);
}
