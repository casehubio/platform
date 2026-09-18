package io.casehub.platform.spring.generator;

import io.casehub.platform.api.FactoryMethod;

import java.util.List;
import java.util.Optional;

// --- Properties interface (lives in core module) ---
interface SampleProperties {
    String name();
    int count();
}

// --- @ConfigMapping extension (lives in Quarkus module) ---
@io.smallrye.config.ConfigMapping(prefix = "sample.config")
interface SampleConfig extends SampleProperties {
}

// --- Core POJOs with various constructor patterns ---

class SimplePojo {
    public SimplePojo() {}
}

class ConfigPojo {
    private final SampleProperties config;
    public ConfigPojo(SampleProperties config) {
        this.config = config;
    }
}

interface SomeInterface {}

class ListPojo {
    private final List<SomeInterface> items;
    public ListPojo(List<SomeInterface> items) {
        this.items = items;
    }
}

class SomeDep {}

class OptionalPojo {
    private final SomeDep dep;
    public OptionalPojo(Optional<SomeDep> dep) {
        this.dep = dep.orElse(null);
    }
}

class FactoryPojo {
    private final List<SomeInterface> items;
    private final SomeDep dep;

    FactoryPojo(List<SomeInterface> items, SomeDep dep) {
        this.items = items;
        this.dep = dep;
    }

    @FactoryMethod
    public static FactoryPojo create(List<SomeInterface> items, Optional<SomeDep> dep) {
        return new FactoryPojo(items, dep.orElse(null));
    }
}

class MixedPojo {
    public MixedPojo(SampleProperties config, List<SomeInterface> items, SomeDep dep) {}
}
