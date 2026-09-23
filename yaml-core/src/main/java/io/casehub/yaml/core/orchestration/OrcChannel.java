package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface OrcChannel<T> {
    void send(T value) throws InterruptedException;
    boolean send(T value, long timeout, TimeUnit unit) throws InterruptedException;
    T receive() throws InterruptedException;
    T receive(long timeout, TimeUnit unit) throws InterruptedException;
    boolean isEmpty();
    void close();
    void close(Throwable cause);
    boolean isErrorClosed();
    Throwable closeError();
}
