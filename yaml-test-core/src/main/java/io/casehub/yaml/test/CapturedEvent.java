package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;

import java.util.Map;

public record CapturedEvent(CloudEvent event, Map<String, Object> data) {

    public String type() {
        return event.getType();
    }

    public String source() {
        return event.getSource().toString();
    }

    @SuppressWarnings("unchecked")
    static CapturedEvent from(CloudEvent event) {
        Map<String, Object> dataMap = Map.of();
        if (event.getData() != null) {
            try {
                var mapper = new ObjectMapper();
                dataMap = mapper.readValue(event.getData().toBytes(), Map.class);
            } catch (Exception e) {
                dataMap = Map.of("_raw", new String(event.getData().toBytes()));
            }
        }
        return new CapturedEvent(event, dataMap);
    }
}
