package io.casehub.platform.datasource.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.path.Path;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "datasource_descriptor")
@IdClass(DataSourceDescriptorEntity.PK.class)
public class DataSourceDescriptorEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @Column(name = "path", length = 1024, nullable = false)
    public String path;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "object_type_key", nullable = false)
    public String objectTypeKey;

    @Column(name = "endpoint_path", length = 1024)
    public String endpointPath;

    @Column(name = "accepted_event_types", columnDefinition = "TEXT")
    public String acceptedEventTypes;

    @Column(name = "properties", columnDefinition = "TEXT")
    public String properties;

    @Column(name = "marshaller_keys", columnDefinition = "TEXT")
    public String marshallerKeys;

    @Column(name = "registered_at", nullable = false)
    public Instant registeredAt;

    public static DataSourceDescriptorEntity fromDomain(DataSourceDescriptor descriptor) {
        var entity = new DataSourceDescriptorEntity();
        entity.path = descriptor.path().value();
        entity.tenancyId = descriptor.tenancyId();
        entity.objectTypeKey = String.valueOf(descriptor.objectType().getTypeKey());
        entity.endpointPath = descriptor.endpointPath() != null
                ? descriptor.endpointPath().value() : null;
        entity.acceptedEventTypes = toJson(descriptor.acceptedEventTypes());
        entity.properties = toJson(descriptor.properties());
        entity.marshallerKeys = toJson(descriptor.marshallerKeys());
        entity.registeredAt = Instant.now();
        return entity;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataSourceDescriptor toDomain() {
        return new DataSourceDescriptor(
                Path.parse(path),
                tenancyId,
                new ClassObjectType(loadClass(objectTypeKey)),
                endpointPath != null ? Path.parse(endpointPath) : null,
                fromJsonSet(acceptedEventTypes),
                fromJsonMap(properties),
                fromJsonMap(marshallerKeys));
    }

    public void updateFrom(DataSourceDescriptor descriptor) {
        endpointPath = descriptor.endpointPath() != null
                ? descriptor.endpointPath().value() : null;
        acceptedEventTypes = toJson(descriptor.acceptedEventTypes());
        properties = toJson(descriptor.properties());
        marshallerKeys = toJson(descriptor.marshallerKeys());
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    private static Set<String> fromJsonSet(String json) {
        if (json == null || json.isEmpty()) return Set.of();
        try {
            return Set.copyOf(MAPPER.readValue(json, new TypeReference<Set<String>>() {}));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON set", e);
        }
    }

    private static Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return Map.copyOf(MAPPER.readValue(json, new TypeReference<Map<String, String>>() {}));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON map", e);
        }
    }

    private static Class<?> loadClass(String typeKey) {
        try {
            return Class.forName(typeKey);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("ObjectType class not found: " + typeKey, e);
        }
    }

    public static class PK implements Serializable {
        public String path;
        public String tenancyId;

        public PK() {}

        public PK(String path, String tenancyId) {
            this.path = path;
            this.tenancyId = tenancyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(path, pk.path) && Objects.equals(tenancyId, pk.tenancyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, tenancyId);
        }
    }
}
