package io.casehub.yaml.core.orchestration;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface OrcMap<K, V> extends OrcPrimitive {
    V get(K key);
    V put(K key, V value);
    V putIfAbsent(K key, V value);
    V computeIfAbsent(K key, Function<K, V> mappingFunction);
    V merge(K key, V value, BiFunction<V, V, V> remappingFunction);
    V remove(K key);
    boolean containsKey(K key);
    int size();
}
