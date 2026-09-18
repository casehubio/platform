package io.casehub.platform.simulation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CloudEventFixtureBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "id", "type", "source", "time", "datacontenttype", "dataschema",
            "subject", "specversion", "data", "data_base64");

    private CloudEventFixtureBuilder() {}

    public static CloudEvent fromMap(final Map<String, Object> map) {
        final String type = requireString(map, "type");
        final String source = requireString(map, "source");
        final String id = map.containsKey("id")
                ? map.get("id").toString()
                : UUID.randomUUID().toString();

        final CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(id)
                .withType(type)
                .withSource(URI.create(source));

        if (map.containsKey("subject")) {
            builder.withSubject((String) map.get("subject"));
        }
        if (map.containsKey("datacontenttype")) {
            builder.withDataContentType((String) map.get("datacontenttype"));
        }
        if (map.containsKey("dataschema")) {
            builder.withDataSchema(URI.create((String) map.get("dataschema")));
        }
        if (map.containsKey("data")) {
            final String contentType = (String) map.getOrDefault("datacontenttype", "application/json");
            builder.withData(contentType, serializeData(map.get("data")));
        }

        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            if (!KNOWN_FIELDS.contains(entry.getKey()) && entry.getValue() != null) {
                builder.withExtension(entry.getKey(), entry.getValue().toString());
            }
        }

        return builder.build();
    }

    public static Map<String, Object> toMap(final CloudEvent event) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", event.getType());
        map.put("source", event.getSource().toString());

        if (event.getSubject() != null) {
            map.put("subject", event.getSubject());
        }
        if (event.getDataContentType() != null) {
            map.put("datacontenttype", event.getDataContentType());
        }
        if (event.getDataSchema() != null) {
            map.put("dataschema", event.getDataSchema().toString());
        }
        if (event.getData() != null) {
            try {
                map.put("data", MAPPER.readValue(event.getData().toBytes(), Object.class));
            } catch (final Exception e) {
                map.put("data", new String(event.getData().toBytes()));
            }
        }

        for (final String extName : event.getExtensionNames()) {
            map.put(extName, event.getExtension(extName));
        }

        return map;
    }

    private static byte[] serializeData(final Object data) {
        if (data instanceof byte[] bytes) {
            return bytes;
        }
        if (data instanceof String s) {
            return s.getBytes();
        }
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize event data", e);
        }
    }

    private static String requireString(final Map<String, Object> map, final String key) {
        final Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("CloudEvent map must contain '" + key + "'");
        }
        return value.toString();
    }
}
