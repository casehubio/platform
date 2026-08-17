package io.casehub.platform.callback.generator.test;

import io.casehub.platform.api.mcp.CallbackEligible;

@CallbackEligible(name = "test-provisioner")
public interface TestProvisioner {

    void provision(String resourceId, int priority);
}
