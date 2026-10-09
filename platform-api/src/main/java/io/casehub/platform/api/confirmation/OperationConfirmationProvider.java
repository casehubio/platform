package io.casehub.platform.api.confirmation;

public interface OperationConfirmationProvider {
    ConfirmationResult confirm(String actorId, String tenancyId,
                                OperationDescriptor operation);
}
