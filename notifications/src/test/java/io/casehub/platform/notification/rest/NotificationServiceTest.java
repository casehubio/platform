package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationClicked;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationReplied;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.ReplyHint;
import io.casehub.platform.api.notification.ReplyRequest;
import io.casehub.platform.notification.inmem.InMemoryNotificationStore;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {

    private InMemoryNotificationStore store;
    private List<NotificationClicked> clickedEvents;
    private List<NotificationReplied> repliedEvents;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationStore(1000);
        clickedEvents = new ArrayList<>();
        repliedEvents = new ArrayList<>();
        var principal = fixedPrincipal("user-1", "tenant-1");
        service = new NotificationService(store, principal,
                new CapturingEvent<>(clickedEvents), new CapturingEvent<>(repliedEvents));
    }

    // --- trackClick ---

    @Test
    void trackClick_returnsNotificationAndFiresEvent() {
        var notif = storeNotification("user-1", "tenant-1", null);

        var result = service.trackClick(notif.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(notif.id());
        assertThat(clickedEvents).hasSize(1);
        assertThat(clickedEvents.getFirst().notification().id()).isEqualTo(notif.id());
    }

    @Test
    void trackClick_returnsEmptyForNonExistent() {
        var result = service.trackClick("non-existent-id");

        assertThat(result).isEmpty();
        assertThat(clickedEvents).isEmpty();
    }

    @Test
    void trackClick_returnsEmptyForWrongUser() {
        var notif = storeNotification("other-user", "tenant-1", null);

        var result = service.trackClick(notif.id());

        assertThat(result).isEmpty();
        assertThat(clickedEvents).isEmpty();
    }

    // --- reply ---

    @Test
    void reply_freeText_firesEventWithValue() {
        var notif = storeNotification("user-1", "tenant-1", ReplyHint.freeText());

        var result = service.reply(notif.id(), new ReplyRequest("my response"));

        assertThat(result).isPresent();
        assertThat(repliedEvents).hasSize(1);
        assertThat(repliedEvents.getFirst().value()).isEqualTo("my response");
    }

    @Test
    void reply_acknowledge_passesNullValue() {
        var notif = storeNotification("user-1", "tenant-1", ReplyHint.acknowledge());

        service.reply(notif.id(), new ReplyRequest("ignored value"));

        assertThat(repliedEvents).hasSize(1);
        assertThat(repliedEvents.getFirst().value()).isNull();
    }

    @Test
    void reply_options_acceptsValidOption() {
        var notif = storeNotification("user-1", "tenant-1",
                ReplyHint.options(List.of("approve", "reject")));

        service.reply(notif.id(), new ReplyRequest("approve"));

        assertThat(repliedEvents).hasSize(1);
        assertThat(repliedEvents.getFirst().value()).isEqualTo("approve");
    }

    @Test
    void reply_options_rejectsInvalidOption() {
        var notif = storeNotification("user-1", "tenant-1",
                ReplyHint.options(List.of("approve", "reject")));

        assertThatThrownBy(() -> service.reply(notif.id(), new ReplyRequest("maybe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    void reply_options_rejectsNull() {
        var notif = storeNotification("user-1", "tenant-1",
                ReplyHint.options(List.of("approve", "reject")));

        assertThatThrownBy(() -> service.reply(notif.id(), new ReplyRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reply_freeText_rejectsNullValue() {
        var notif = storeNotification("user-1", "tenant-1", ReplyHint.freeText());

        assertThatThrownBy(() -> service.reply(notif.id(), new ReplyRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reply value required");
    }

    @Test
    void reply_noReplyHint_throwsIllegalArgument() {
        var notif = storeNotification("user-1", "tenant-1", null);

        assertThatThrownBy(() -> service.reply(notif.id(), new ReplyRequest("value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept replies");
    }

    @Test
    void reply_dismissedNotification_throwsIllegalArgument() {
        var notif = storeNotification("user-1", "tenant-1", ReplyHint.freeText());
        store.dismiss(notif.id(), "user-1", "tenant-1");

        assertThatThrownBy(() -> service.reply(notif.id(), new ReplyRequest("value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot reply to dismissed");
    }

    @Test
    void reply_returnsEmptyForNonExistent() {
        var result = service.reply("non-existent", new ReplyRequest("value"));

        assertThat(result).isEmpty();
        assertThat(repliedEvents).isEmpty();
    }

    // --- helpers ---

    private Notification storeNotification(String userId, String tenancyId, ReplyHint hint) {
        var source = new NotificationSource("evt-1", "work-item", "wi-1", "actor-1");
        var input = new NotificationInput(userId, tenancyId, "Test Title", "Test Body",
                "test.category", NotificationSeverity.INFO, "/action", source, hint);
        return store.store(input);
    }

    private static CurrentPrincipal fixedPrincipal(String actorId, String tenancyId) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return actorId; }
            @Override public String tenancyId() { return tenancyId; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }

    @SuppressWarnings("unchecked")
    static class CapturingEvent<T> implements Event<T> {
        private final List<T> captured;

        CapturingEvent(List<T> captured)                                                        {this.captured = captured;}

        @Override
        public void fire(T event)                                                               {captured.add(event);}

        @Override
        public <U extends T> CompletionStage<U> fireAsync(U event)                              {
                                                                                                    captured.add(event);
                                                                                                    return (CompletionStage<U>) CompletableFuture.completedFuture(event);
                                                                                                }

        @Override
        public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {return fireAsync(event);}

        @Override
        public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers)        {throw new UnsupportedOperationException();}

        @Override
        public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers)  {throw new UnsupportedOperationException();}

        @Override
        public Event<T> select(Annotation... qualifiers)                                        {throw new UnsupportedOperationException();}
    }
}
