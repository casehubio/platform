package io.casehub.platform.graphql.scalar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.graphql.api.CustomScalar;
import io.smallrye.graphql.api.CustomStringScalar;

import java.util.Map;

@CustomScalar("JSON")
public class Json implements CustomStringScalar {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String,Object>>MAP_TYPE =new TypeReference<>()

    {}

    ;

    private final Map<String,Object>value;

    public Json(String json) {
        if (json == null || json.equals("null")) {
            this.value = null;
        } else {
            try {
                this.value = MAPPER.readValue(json, MAP_TYPE);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
            }
        }
    }

    private Json(Map<

    String,Object>value)

    {
        this.value = value;
    }

    public static Json of(Map<

    String,Object>map)

    {
        return new Json(map);
    }

    public static Json ofString(String json) {
        return new Json(json);
    }

    public Map<String,Object>

    value() {
        return value;
    }

    @Override
    public String stringValueForSerialization() {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    @Override
    public String toString() {
        return stringValueForSerialization();
    }
}
