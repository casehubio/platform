package io.casehub.yaml.core.type;

import java.util.Map;

public interface TypedSchema {
    ValueType typeOf(String name);
    Map<String, ValueType> schema();
}
