package io.casehub.yaml.jackson;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;

class LocationTrackingCollectionDeserializer extends DelegatingDeserializer {

    LocationTrackingCollectionDeserializer(JsonDeserializer<?> delegate) {
        super(delegate);
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegate) {
        return new LocationTrackingCollectionDeserializer(newDelegate);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
        JsonLocation loc = p.currentTokenLocation();
        Object result = super.deserialize(p, ctxt);
        LocationTrackingHelper.recordLocation(ctxt, result, loc);
        return result;
    }
}
