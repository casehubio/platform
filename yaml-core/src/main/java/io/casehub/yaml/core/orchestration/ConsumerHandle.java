package io.casehub.yaml.core.orchestration;

public interface ConsumerHandle extends OrcPrimitive {
    void pause();
    void resume();
    boolean isRunning();
    boolean isReleased();
    void registerThread(Thread thread);
}
