package io.casehub.platform.callback.generator.test;

import io.casehub.platform.api.mcp.CallbackEligible;

@CallbackEligible
public interface TestDefaultNameSpi {

    void doWork(String input);
}
