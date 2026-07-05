package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.subscription.NotificationTemplate;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

/**
 * Resolves a {@link NotificationTemplate} against a POJO to produce a {@link NotificationInput}.
 *
 * <p>Uses MethodHandle to read POJO fields for {@code {placeholder}} substitution,
 * entityIdField, and actorIdField. Generates a UUIDv7 for the eventId.
 *
 * <p>Returns {@code null} and logs WARN if entityIdField or actorIdField resolve to null.
 */
public final class TemplateResolver {

    private static final Logger LOG = Logger.getLogger(TemplateResolver.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private TemplateResolver() {
        // Utility class — no instances
    }

    /**
     * Resolves a notification template against a POJO into a NotificationInput.
     *
     * @param template  notification template with placeholder patterns
     * @param pojo      event POJO providing field values
     * @param userId    notification recipient
     * @param tenancyId tenant context
     * @return resolved NotificationInput, or null if required fields (entityId, actorId) are missing
     */
    public static NotificationInput resolve(final NotificationTemplate template,
                                            final Object pojo,
                                            final String userId,
                                            final String tenancyId) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(pojo, "pojo");

        final String entityId = extractField(pojo, template.entityIdField());
        if (entityId == null) {
            LOG.warnf("entityIdField '%s' resolved to null on %s — skipping notification",
                    template.entityIdField(), pojo.getClass().getSimpleName());
            return null;
        }

        final String actorId = extractField(pojo, template.actorIdField());
        if (actorId == null) {
            LOG.warnf("actorIdField '%s' resolved to null on %s — skipping notification",
                    template.actorIdField(), pojo.getClass().getSimpleName());
            return null;
        }

        final String title = substitutePlaceholders(template.titlePattern(), pojo);
        final String body = template.bodyPattern() != null
                ? substitutePlaceholders(template.bodyPattern(), pojo)
                : null;
        final String actionUrl = template.actionUrlPattern() != null
                ? substitutePlaceholders(template.actionUrlPattern(), pojo)
                : null;

        final var source = new NotificationSource(
                UUIDv7.generate(),
                template.entityType(),
                entityId,
                actorId);

        return new NotificationInput(
                userId,
                tenancyId,
                title,
                body,
                template.category(),
                template.severity(),
                actionUrl,
                source);
    }

    /**
     * Substitutes {@code {fieldName}} placeholders in the pattern with values extracted
     * from the POJO via MethodHandle. Unresolvable placeholders are left as-is.
     */
    private static String substitutePlaceholders(final String pattern, final Object pojo) {
        final Matcher matcher = PLACEHOLDER.matcher(pattern);
        final StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            final String fieldName = matcher.group(1);
            final String value = extractField(pojo, fieldName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Extracts a field value from a POJO by calling its no-arg accessor method via MethodHandle.
     * Returns null if the field is not found or the value is null.
     */
    private static String extractField(final Object pojo, final String fieldName) {
        try {
            var method = pojo.getClass().getMethod(fieldName);
            var handle = MethodHandles.lookup().unreflect(method);
            final Object value = handle.invoke(pojo);
            return value != null ? value.toString() : null;
        } catch (Throwable e) {
            return null;
        }
    }
}
