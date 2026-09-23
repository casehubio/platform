package io.casehub.platform.observability.quarkus;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.stream.Stream;

class SingletonInstance<T> implements Instance<T> {

    private final T value;

    SingletonInstance(T value) {
        this.value = value;
    }

    static <T> SingletonInstance<T> of(T value) {
        return new SingletonInstance<>(value);
    }

    static <T> SingletonInstance<T> empty() {
        return new SingletonInstance<>(null);
    }

    @Override public T get() { return value; }
    @Override public boolean isUnsatisfied() { return value == null; }
    @Override public boolean isAmbiguous() { return false; }
    @Override public boolean isResolvable() { return value != null; }
    @Override public Instance<T> select(Annotation... qualifiers) { return this; }
    @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    @Override public Stream<T> stream() { return value != null ? Stream.of(value) : Stream.empty(); }
    @Override public void destroy(T instance) {}
    @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
    @Override public Iterable<Handle<T>> handles() { throw new UnsupportedOperationException(); }
    @Override public Iterator<T> iterator() { return stream().iterator(); }
}
