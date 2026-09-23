package io.casehub.yaml.core.orchestration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class DefaultOrcMap<K, V> implements OrcMap<K, V> {

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

    @Override
    public V get(K key) { return map.get(key); }

    @Override
    public V put(K key, V value) { return map.put(key, value); }

    @Override
    public V putIfAbsent(K key, V value) { return map.putIfAbsent(key, value); }

    @Override
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) { return map.computeIfAbsent(key, mappingFunction); }

    @Override
    public V merge(K key, V value, BiFunction<V, V, V> remappingFunction) { return map.merge(key, value, remappingFunction); }

    @Override
    public V remove(K key) { return map.remove(key); }

    @Override
    public boolean containsKey(K key) { return map.containsKey(key); }

    @Override
    public int size() { return map.size(); }
}
