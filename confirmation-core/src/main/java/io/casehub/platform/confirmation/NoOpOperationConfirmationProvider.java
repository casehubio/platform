package io.casehub.platform.confirmation;

import io.casehub.platform.api.confirmation.ConfirmationRequiredException;
import io.casehub.platform.api.confirmation.ConfirmationResult;
import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.api.confirmation.OperationDescriptor;

public class NoOpOperationConfirmationProvider implements OperationConfirmationProvider {

    @Override
    public ConfirmationResult confirm(String actorId, String tenancyId,
                                       OperationDescriptor operation) {
        throw new ConfirmationRequiredException(operation, ConfirmationResult.DENIED);
    }
}
