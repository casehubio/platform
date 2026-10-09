package io.casehub.platform.confirmation;

import io.casehub.platform.api.confirmation.ConfirmationResult;
import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.api.confirmation.OperationDescriptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingOperationConfirmationProvider implements OperationConfirmationProvider {

    private final ConfirmationResult fixedResult;
    private final List<RecordedConfirmation> recordings = new CopyOnWriteArrayList<>();

    public RecordingOperationConfirmationProvider(ConfirmationResult fixedResult) {
        this.fixedResult = fixedResult;
    }

    @Override
    public ConfirmationResult confirm(String actorId, String tenancyId,
                                       OperationDescriptor operation) {
        recordings.add(new RecordedConfirmation(actorId, tenancyId, operation));
        return fixedResult;
    }

    public List<RecordedConfirmation> recordings() {
        return List.copyOf(recordings);
    }

    public record RecordedConfirmation(String actorId, String tenancyId,
                                        OperationDescriptor operation) {}
}
