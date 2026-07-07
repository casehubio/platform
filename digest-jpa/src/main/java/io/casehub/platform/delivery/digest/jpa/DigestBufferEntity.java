package io.casehub.platform.delivery.digest.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.UUIDv7;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "digest_buffer")
public class DigestBufferEntity extends PanacheEntityBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "channel_id", nullable = false)
    public String channelId;

    @Column(name = "notification_json", nullable = false, columnDefinition = "TEXT")
    public String notificationJson;

    @Column(name = "buffered_at", nullable = false)
    public Instant bufferedAt;

    public static DigestBufferEntity fromNotificationInput(
            String userId, String tenancyId, String channelId,
            NotificationInput input) {
        var entity = new DigestBufferEntity();
        entity.id = UUID.fromString(UUIDv7.generate());
        entity.userId = userId;
        entity.tenancyId = tenancyId;
        entity.channelId = channelId;
        entity.notificationJson = serialize(input);
        entity.bufferedAt = Instant.now();
        return entity;
    }

    public NotificationInput toNotificationInput() {
        try {
            return JSON.readValue(notificationJson, NotificationInput.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize notification JSON", e);
        }
    }

    private static String serialize(NotificationInput input) {
        try {
            return JSON.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize NotificationInput to JSON", e);
        }
    }
}
