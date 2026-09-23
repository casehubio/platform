package io.casehub.yaml.core.runtime;

@FunctionalInterface
public interface Condition {
    boolean evaluate();

    default Condition and(Condition other) {
        return () -> this.evaluate() && other.evaluate();
    }

    default Condition or(Condition other) {
        return () -> this.evaluate() || other.evaluate();
    }

    default Condition not() {
        return () -> !this.evaluate();
    }

    default Condition xor(Condition other) {
        return () -> this.evaluate() ^ other.evaluate();
    }

    static Condition always() { return () -> true; }
    static Condition never() { return () -> false; }
}
