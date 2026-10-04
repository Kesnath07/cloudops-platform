package io.cloudops.platform.notifications;

/**
 * Delivery channel for incident notifications.
 */
public interface IncidentNotifier {

    void send(IncidentNotification notification);
}
