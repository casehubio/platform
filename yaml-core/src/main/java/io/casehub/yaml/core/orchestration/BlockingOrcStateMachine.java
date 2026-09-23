package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.Set;

public interface BlockingOrcStateMachine<S extends Enum<S>> extends OrcStateMachine<S> {

    void awaitState(S target) throws InterruptedException;

    boolean awaitState(S target, Duration timeout) throws InterruptedException;

    void awaitTransition(S from, S to) throws InterruptedException;

    S awaitAnyState(Set<S> targets) throws InterruptedException;

    S awaitAnyState(Set<S> targets, Duration timeout) throws InterruptedException;
}
