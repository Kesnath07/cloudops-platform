package io.cloudops.platform.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local-development channel: writes notifications to the application log instead of AWS.
 */
class LoggingIncidentNotifier implements IncidentNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingIncidentNotifier.class);

    @Override
    public void send(IncidentNotification notification) {
        log.info("Incident notification [{}] {}: {}", notification.eventType(), notification.subject(),
                notification.body());
    }
}
