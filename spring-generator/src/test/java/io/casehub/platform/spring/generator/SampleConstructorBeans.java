package io.casehub.platform.spring.generator;

import io.quarkus.arc.DefaultBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
class SampleConstructorBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public SimplePojo simplePojo() {
        return new SimplePojo();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public ConfigPojo configPojo(SampleConfig config) {
        return new ConfigPojo(config);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public ListPojo listPojo(Instance<SomeInterface> items) {
        return new ListPojo(items.stream().toList());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public OptionalPojo optionalPojo(Instance<SomeDep> dep) {
        return new OptionalPojo(dep.stream().findFirst());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public FactoryPojo factoryPojo(Instance<SomeInterface> items, Instance<SomeDep> dep) {
        return FactoryPojo.create(items.stream().toList(), dep.stream().findFirst());
    }

    @Produces
    @ApplicationScoped
    public SupplierDepPojo supplierDepPojo(SomeDep dep,
                                            Instance<SomeDep> optionalDep) {
        return new SupplierDepPojo(dep,
                optionalDep.isResolvable() ? optionalDep::get : null);
    }

    @Produces
    @ApplicationScoped
    public EventConsumerPojo eventConsumerPojo(SomeDep dep,
                                               jakarta.enterprise.event.Event<String> stringEvent) {
        return new EventConsumerPojo(dep, stringEvent::fire);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public WildcardListPojo wildcardListPojo(Instance<GenericInterface<?>> items) {
        return new WildcardListPojo(items.stream().toList());
    }

}

@ApplicationScoped
class SampleInitBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public SimplePojo initPojo() {
        return new SimplePojo();
    }

    @PostConstruct
    void validate() {
    }
}

@ApplicationScoped
class SamplePriorityBeans {

    @Produces
    @Priority(42)
    @ApplicationScoped
    public SimplePojo priorityPojo() {
        return new SimplePojo();
    }
}

@jakarta.inject.Qualifier
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.METHOD,
        java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER})
@interface SampleQualifier {}

@ApplicationScoped
class SampleQualifierBeans {

    @Produces
    @ApplicationScoped
    public SomeInterface qualifiedImpl() {
        return new SomeInterface() {};
    }

    @Produces
    @ApplicationScoped
    public ListPojo compositePojo(@SampleQualifier Instance<SomeInterface> items) {
        return new ListPojo(items.stream().toList());
    }
}
