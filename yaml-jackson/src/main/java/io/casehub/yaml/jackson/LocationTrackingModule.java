package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class LocationTrackingModule extends SimpleModule {

    public static final String LOCATION_INDEX_KEY = "io.casehub.yaml.locationIndex";
    public static final String FILE_PATH_KEY = "io.casehub.yaml.filePath";

    public LocationTrackingModule() {
        super("location-tracking");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addBeanDeserializerModifier(new LocationTrackingModifier());
    }
}
