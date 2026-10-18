package io.casehub.platform.agent;

import java.util.Objects;

public record ResponseFormat(
        ResponseFormatType type,
        String name,
        String schema
) {
    public static final ResponseFormat TEXT =
            new ResponseFormat(ResponseFormatType.TEXT, null, null);

    public ResponseFormat {
        Objects.requireNonNull(type, "type");
        if (type == ResponseFormatType.JSON && name != null && schema == null) {
            throw new IllegalArgumentException("JSON with name requires schema");
        }
        if (type == ResponseFormatType.JSON && name == null && schema != null) {
            throw new IllegalArgumentException("JSON with schema requires name");
        }
    }

    public static ResponseFormat json(String name, String schema) {
        return new ResponseFormat(ResponseFormatType.JSON,
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(schema, "schema"));
    }

    public static ResponseFormat jsonAny() {
        return new ResponseFormat(ResponseFormatType.JSON, null, null);
    }
}
