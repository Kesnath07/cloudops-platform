package io.cloudops.platform.notifications;

import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;
import io.cloudops.platform.incidents.events.IncidentOpenedEvent;
import io.cloudops.platform.incidents.events.IncidentUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;

/**
 * Turns committed incident events into notifications. Listening after commit guarantees nobody
 * is paged for an incident that was rolled back; running asynchronously keeps notification
 * latency out of the API response time.
 */
@Component
class IncidentNotificationListener {

    private final IncidentNotifier notifier;
    private final Severity minimumSeverity;

    IncidentNotificationListener(IncidentNotifier notifier, NotificationProperties properties) {
        this.notifier = notifier;
        this.minimumSeverity = properties.minimumSeverity();
    }

    @Async
    @TransactionalEventListener
    void onOpened(IncidentOpenedEvent event) {
        if (!event.severity().isAtLeast(minimumSeverity)) {
            return;
        }
        notifier.send(new IncidentNotification(event.incidentId(), "INCIDENT_OPENED", event.severity(),
                "[%s] %s: %s".formatted(event.severity(), event.workloadName(), event.title()),
                lines("A %s incident was opened on %s by %s.".formatted(
                                event.severity(), event.workloadName(), event.openedBy()),
                        "",
                        "Title: " + event.title(),
                        "Incident: " + event.incidentId())));
    }

    @Async
    @TransactionalEventListener
    void onUpdated(IncidentUpdatedEvent event) {
        if (!event.severity().isAtLeast(minimumSeverity) && !event.previousSeverity().isAtLeast(minimumSeverity)) {
            return;
        }
        String eventType;
        if (event.statusChanged() && event.status() == IncidentStatus.RESOLVED) {
            eventType = "INCIDENT_RESOLVED";
        } else if (event.statusChanged()) {
            eventType = "INCIDENT_" + event.status().name();
        } else if (event.escalated()) {
            eventType = "INCIDENT_ESCALATED";
        } else {
            return;
        }
        notifier.send(new IncidentNotification(event.incidentId(), eventType, event.severity(),
                "[%s] %s %s: %s".formatted(event.severity(), event.workloadName(),
                        eventType.substring("INCIDENT_".length()).toLowerCase(Locale.ROOT), event.title()),
                lines("Incident on %s changed by %s.".formatted(event.workloadName(), event.updatedBy()),
                        "",
                        "Status: %s -> %s".formatted(event.previousStatus(), event.status()),
                        "Severity: %s -> %s".formatted(event.previousSeverity(), event.severity()),
                        "Update: " + event.message(),
                        "Incident: " + event.incidentId())));
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }
}
