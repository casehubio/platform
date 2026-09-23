package io.casehub.yaml.core.orchestration;

import java.time.Duration;

public interface SpawnedTask {

    String name();

    boolean isDone();

    boolean isFailed();

    Throwable exception();

    void join() throws InterruptedException;

    boolean join(Duration timeout) throws InterruptedException;
}
