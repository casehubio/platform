package io.casehub.platform.confirmation;

import io.casehub.platform.api.confirmation.ConfirmationRequiredException;
import io.casehub.platform.api.confirmation.ConfirmationResult;
import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.api.confirmation.OperationDescriptor;

public class ConfirmationInterceptorCore {

    private final OperationConfirmationProvider provider;

    public ConfirmationInterceptorCore(OperationConfirmationProvider provider) {
        this.provider = provider;
    }

    public void requireConfirmation(String actorId, String tenancyId,
                                     OperationDescriptor descriptor) {
        ConfirmationResult result = provider.confirm(actorId, tenancyId, descriptor);
        if (result != ConfirmationResult.CONFIRMED) {
            throw new ConfirmationRequiredException(descriptor, result);
        }
    }
}
