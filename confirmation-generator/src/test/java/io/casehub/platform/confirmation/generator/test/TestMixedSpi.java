package io.casehub.platform.confirmation.generator.test;

import io.casehub.platform.api.confirmation.RequiresConfirmation;

public interface TestMixedSpi {
    @RequiresConfirmation(summary = "Delete ${resourceId}")
    void deleteResource(String resourceId);

    String getStatus(String resourceId);
}
