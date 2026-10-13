package io.casehub.yaml.core.orchestration;

import java.util.concurrent.locks.LockSupport;

public final class DefaultConsumerHandle implements ConsumerHandle {

    private final String name;
    private volatile boolean running = true;
    private volatile boolean released = false;
    private volatile Thread consumerThread;

    public DefaultConsumerHandle(String name) {
        this.name = name;
    }

    @Override
    public void pause() {
        running = false;
    }

    @Override
    public void resume() {
        running = true;
        Thread t = consumerThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @Override
    public void registerThread(Thread thread) {
        this.consumerThread = thread;
    }

    @Override
    public void releaseForClose() {
        released = true;
        Thread t = consumerThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    @Override
    public String toString() {
        return "ConsumerHandle[" + name + ", running=" + running + ", released=" + released + "]";
    }
}
