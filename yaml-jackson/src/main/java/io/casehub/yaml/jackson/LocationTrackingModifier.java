package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;

class LocationTrackingModifier extends BeanDeserializerModifier {

    @Override
    public JsonDeserializer<?> modifyMapDeserializer(DeserializationConfig config,
                                                     MapType type,
                                                     BeanDescription beanDesc,
                                                     JsonDeserializer<?> deserializer) {
        return new LocationTrackingMapDeserializer(deserializer);
    }

    @Override
    public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config,
                                                            CollectionType type,
                                                            BeanDescription beanDesc,
                                                            JsonDeserializer<?> deserializer) {
        return new LocationTrackingCollectionDeserializer(deserializer);
    }
}
