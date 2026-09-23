package io.casehub.yaml.core.orchestration;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultOrcFlag implements OrcFlag {

    private final AtomicBoolean flag = new AtomicBoolean(false);

    @Override
    public void set() { flag.set(true); }

    @Override
    public void clear() { flag.set(false); }

    @Override
    public boolean toggle() {
        boolean prev;
        do {
            prev = flag.get();
        } while (!flag.compareAndSet(prev, !prev));
        return prev;
    }

    @Override
    public boolean get() { return flag.get(); }
}
