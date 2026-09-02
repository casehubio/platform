package io.casehub.yaml.core.foreach;

import io.casehub.yaml.core.resolver.VariableResolver;

public interface ForEachAdapter<E> {

    E stamp(E template, String stampedId, VariableResolver scopedResolver);

    ForEachDirective getForEach(E element);

    String getWhen(E element);

    default java.util.List<Reference> getReferences(E element)               {return java.util.List.of();}

    default E withReferences(E element, java.util.List<Reference> rewritten) {return element;}

    record Reference(String targetId, boolean optional) {}

}
