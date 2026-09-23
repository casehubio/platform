package io.casehub.yaml.core.orchestration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultOrcChannel<T> implements OrcChannel<T> {

    private final String name;
    private final BlockingQueue<T> queue;
    private volatile boolean closed;
    private final AtomicReference<Throwable> closeError = new AtomicReference<>();

    public DefaultOrcChannel(String name) {
        this.name = name;
        this.queue = new LinkedBlockingQueue<>();
        this.closed = false;
    }

    public DefaultOrcChannel(String name, int capacity) {
        this.name = name;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.closed = false;
    }

    @Override
    public void send(T value) throws InterruptedException {
        if (closed) {throw new ChannelClosedException(name);}
        queue.put(value);
        if (closed) {throw new ChannelClosedException(name);}
    }

    @Override
    public boolean send(T value, long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) {throw new ChannelClosedException(name);}
        boolean offered = queue.offer(value, timeout, unit);
        if (offered && closed) {throw new ChannelClosedException(name);}
        return offered;
    }

    @Override
    public T receive() throws InterruptedException {
        while (true) {
            T value = queue.poll(100, TimeUnit.MILLISECONDS);
            if (value != null) return value;
            if (closed) {
                T remaining = queue.poll();
                if (remaining != null) return remaining;
                Throwable error = closeError.get();
                if (error != null) {
                    throw new ChannelClosedException(name, error);
                }
                return null;
            }
        }
    }

    @Override
    public T receive(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                T last = queue.poll();
                return last;
            }
            long pollMs = Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 100);
            if (pollMs <= 0) pollMs = 1;
            T value = queue.poll(pollMs, TimeUnit.MILLISECONDS);
            if (value != null) return value;
            if (closed) {
                T remaining = queue.poll();
                if (remaining != null) return remaining;
                Throwable error = closeError.get();
                if (error != null) {
                    throw new ChannelClosedException(name, error);
                }
                return null;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void close(Throwable cause) {
        closeError.set(cause);
        closed = true;
    }

    @Override
    public boolean isErrorClosed() {
        return closed && closeError.get() != null;
    }

    @Override
    public Throwable closeError() {
        return closeError.get();
    }

    @Override
    public void releaseForClose() {
        close();
    }
}
