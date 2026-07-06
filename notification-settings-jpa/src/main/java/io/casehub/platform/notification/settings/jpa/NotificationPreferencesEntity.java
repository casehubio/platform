package io.casehub.platform.notification.settings.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreferencesEntity.PreferencesPK.class)
public class NotificationPreferencesEntity extends PanacheEntityBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, ChannelPreference>> CHANNEL_MAP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<QuietHours> QUIET_HOURS_TYPE = new TypeReference<>() {};

    @Id
    @Column(name = "user_id", nullable = false)
    public String userId;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "channel_defaults_json", columnDefinition = "TEXT")
    public String channelDefaultsJson;

    @Column(name = "quiet_hours_json", columnDefinition = "TEXT")
    public String quietHoursJson;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * Build entity from update input. Upsert semantics — creates new or updates existing.
     */
    static NotificationPreferencesEntity fromUpdate(String userId, String tenancyId,
                                                     NotificationPreferenceUpdate update,
                                                     NotificationPreferencesEntity existing) {
        NotificationPreferencesEntity entity = new NotificationPreferencesEntity();
        entity.userId = userId;
        entity.tenancyId = tenancyId;
        entity.updatedAt = Instant.now();

        // Channel defaults
        Map<String, ChannelPreference> channelDefaults;
        if (update.channelDefaults() != null) {
            channelDefaults = update.channelDefaults();
        } else if (existing != null && existing.channelDefaultsJson != null) {
            channelDefaults = existing.deserializeChannelDefaults();
        } else {
            channelDefaults = Map.of();
        }
        entity.channelDefaultsJson = serializeChannelDefaults(channelDefaults);

        // Quiet hours
        QuietHours quietHours;
        if (update.clearQuietHours()) {
            quietHours = null;
        } else if (update.quietHours() != null) {
            quietHours = update.quietHours();
        } else if (existing != null && existing.quietHoursJson != null) {
            quietHours = existing.deserializeQuietHours();
        } else {
            quietHours = null;
        }
        entity.quietHoursJson = quietHours != null ? serializeQuietHours(quietHours) : null;

        return entity;
    }

    /**
     * Convert entity to domain record.
     */
    NotificationPreferences toPreferences() {
        Map<String, ChannelPreference> channelDefaults = deserializeChannelDefaults();
        QuietHours quietHours = deserializeQuietHours();

        return new NotificationPreferences(
                userId,
                tenancyId,
                channelDefaults,
                quietHours,
                updatedAt
        );
    }

    private Map<String, ChannelPreference> deserializeChannelDefaults() {
        if (channelDefaultsJson == null || channelDefaultsJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return JSON.readValue(channelDefaultsJson, CHANNEL_MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize channelDefaults JSON", e);
        }
    }

    private QuietHours deserializeQuietHours() {
        if (quietHoursJson == null || quietHoursJson.isEmpty()) {
            return null;
        }
        try {
            return JSON.readValue(quietHoursJson, QUIET_HOURS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize quietHours JSON", e);
        }
    }

    private static String serializeChannelDefaults(Map<String, ChannelPreference> channelDefaults) {
        if (channelDefaults == null || channelDefaults.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(channelDefaults);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize channelDefaults to JSON", e);
        }
    }

    private static String serializeQuietHours(QuietHours quietHours) {
        if (quietHours == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(quietHours);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quietHours to JSON", e);
        }
    }

    /**
     * Composite primary key for (userId, tenancyId).
     */
    public static class PreferencesPK implements Serializable {
        public String userId;
        public String tenancyId;

        public PreferencesPK() {}

        public PreferencesPK(String userId, String tenancyId) {
            this.userId = userId;
            this.tenancyId = tenancyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PreferencesPK that = (PreferencesPK) o;
            return Objects.equals(userId, that.userId) && Objects.equals(tenancyId, that.tenancyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, tenancyId);
        }
    }
}
