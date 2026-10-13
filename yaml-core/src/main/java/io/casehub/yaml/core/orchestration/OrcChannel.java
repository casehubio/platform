package io.casehub.yaml.core.orchestration;

import java.util.concurrent.TimeUnit;

public interface OrcChannel<T> extends OrcPrimitive {
    void send(T value) throws InterruptedException;
    boolean send(T value, long timeout, TimeUnit unit) throws InterruptedException;
    T receive() throws InterruptedException;
    T receive(long timeout, TimeUnit unit) throws InterruptedException;
    boolean isEmpty();

    boolean isClosed();

    void close();
    void close(Throwable cause);
    boolean isErrorClosed();
    Throwable closeError();
}
