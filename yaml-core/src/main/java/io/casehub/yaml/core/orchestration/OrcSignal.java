package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface OrcSignal extends OrcPrimitive {
    void signal();
    void signal(Object payload);
    void await() throws InterruptedException;
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;
    Object payload();
    boolean isSignalled();
}
