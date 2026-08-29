package io.casehub.yaml.core.foreach;

import io.casehub.yaml.core.resolver.VariableResolver;

public interface ForEachAdapter<E> {

    E stamp(E template, String stampedId, VariableResolver scopedResolver);

    Object getForEach(E element);

    String getId(E element);

    String getWhen(E element);
}
