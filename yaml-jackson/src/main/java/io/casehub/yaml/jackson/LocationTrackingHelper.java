package io.casehub.yaml.jackson;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.DeserializationContext;
import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.error.SourceLocation;

final class LocationTrackingHelper {

    private LocationTrackingHelper() {}

    static void recordLocation(DeserializationContext ctxt, Object target, JsonLocation loc) {
        Object indexObj = ctxt.getAttribute(LocationTrackingModule.LOCATION_INDEX_KEY);
        if (!(indexObj instanceof LocationIndex index)) {
            return;
        }
        String filePath = (String) ctxt.getAttribute(LocationTrackingModule.FILE_PATH_KEY);
        index.put(target, new SourceLocation(filePath, loc.getLineNr(), loc.getColumnNr()));
    }
}
