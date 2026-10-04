package io.cloudops.platform.notifications;

import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;
import io.cloudops.platform.incidents.events.IncidentOpenedEvent;
import io.cloudops.platform.incidents.events.IncidentUpdatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentNotificationListenerTest {

    private final List<IncidentNotification> sent = new ArrayList<>();
    private final IncidentNotificationListener listener = new IncidentNotificationListener(sent::add,
            new NotificationProperties(NotificationProperties.Channel.LOG, null, Severity.SEV2));

    private static IncidentOpenedEvent opened(Severity severity) {
        return new IncidentOpenedEvent(UUID.randomUUID(), UUID.randomUUID(), "payments-api", "Card declines",
                severity, "Dana", Instant.now());
    }

    private static IncidentUpdatedEvent updated(IncidentStatus from, IncidentStatus to, Severity before, Severity after) {
        return new IncidentUpdatedEvent(UUID.randomUUID(), UUID.randomUUID(), "payments-api", "Card declines",
                from, to, before, after, "Status update", "Dana", Instant.now());
    }

    @Test
    void notifiesWhenSevereIncidentOpens() {
        listener.onOpened(opened(Severity.SEV1));

        assertThat(sent).singleElement().satisfies(notification -> {
            assertThat(notification.eventType()).isEqualTo("INCIDENT_OPENED");
            assertThat(notification.subject()).isEqualTo("[SEV1] payments-api: Card declines");
            assertThat(notification.body()).contains("opened on payments-api by Dana");
        });
    }

    @Test
    void ignoresIncidentsBelowThreshold() {
        listener.onOpened(opened(Severity.SEV3));
        listener.onUpdated(updated(IncidentStatus.OPEN, IncidentStatus.RESOLVED, Severity.SEV4, Severity.SEV4));

        assertThat(sent).isEmpty();
    }

    @Test
    void notifiesOnStatusChangesAndEscalation() {
        listener.onUpdated(updated(IncidentStatus.OPEN, IncidentStatus.MITIGATED, Severity.SEV2, Severity.SEV2));
        listener.onUpdated(updated(IncidentStatus.MITIGATED, IncidentStatus.RESOLVED, Severity.SEV2, Severity.SEV2));
        listener.onUpdated(updated(IncidentStatus.OPEN, IncidentStatus.OPEN, Severity.SEV3, Severity.SEV1));

        assertThat(sent).extracting(IncidentNotification::eventType)
                .containsExactly("INCIDENT_MITIGATED", "INCIDENT_RESOLVED", "INCIDENT_ESCALATED");
    }

    @Test
    void plainCommentsDoNotNotify() {
        listener.onUpdated(updated(IncidentStatus.OPEN, IncidentStatus.OPEN, Severity.SEV1, Severity.SEV1));
        listener.onUpdated(updated(IncidentStatus.OPEN, IncidentStatus.OPEN, Severity.SEV1, Severity.SEV2));

        assertThat(sent).isEmpty();
    }
}
