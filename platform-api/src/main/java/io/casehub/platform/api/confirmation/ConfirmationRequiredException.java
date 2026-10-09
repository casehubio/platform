package io.casehub.platform.api.confirmation;

public class ConfirmationRequiredException extends RuntimeException {

    private final OperationDescriptor operation;
    private final ConfirmationResult result;

    public ConfirmationRequiredException(OperationDescriptor operation,
                                          ConfirmationResult result) {
        super("Operation confirmation " + result.name().toLowerCase()
              + ": " + operation.summary());
        this.operation = operation;
        this.result = result;
    }

    public OperationDescriptor operation() {
        return operation;
    }

    public ConfirmationResult result() {
        return result;
    }
}
