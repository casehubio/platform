package io.casehub.platform.simulation.generator.test;

public interface TestCapability extends TestBaseCapability {
    String process(String input);
    void execute();

    static TestCapability noOp() {
        return new TestCapability() {
            @Override public String process(String input) { return null; }
            @Override public void execute() {}
            @Override public void close() {}
        };
    }

    private void internalHelper() {}
}