package io.casehub.platform.api.acl;

public record ResourceId(String type, String id) {

    public ResourceId {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (type.contains(":")) {
            throw new IllegalArgumentException("type must not contain ':'");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    @com.fasterxml.jackson.annotation.JsonValue
    @Override
    public String toString() {
        return type + ":" + id;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ResourceId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException(
                    "ResourceId must be 'type:id', got: " + value);
        }
        return new ResourceId(value.substring(0, colon), value.substring(colon + 1));
    }

    public static ResourceId fromString(String value) {
        return parse(value);
    }

}
