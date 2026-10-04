package io.cloudops.platform.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * Publishes notifications to an SNS topic. Delivery failures are logged rather than propagated:
 * the incident is already committed, and a notification outage must not fail incident handling.
 */
class SnsIncidentNotifier implements IncidentNotifier {

    private static final Logger log = LoggerFactory.getLogger(SnsIncidentNotifier.class);
    private static final int SNS_SUBJECT_LIMIT = 100;

    private final SnsClient sns;
    private final String topicArn;

    SnsIncidentNotifier(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void send(IncidentNotification notification) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .subject(truncate(notification.subject()))
                .message(notification.body())
                .messageAttributes(Map.of(
                        "eventType", stringAttribute(notification.eventType()),
                        "severity", stringAttribute(notification.severity().name())))
                .build();
        try {
            String messageId = sns.publish(request).messageId();
            log.info("Published {} notification for incident {} as SNS message {}",
                    notification.eventType(), notification.incidentId(), messageId);
        } catch (SdkException ex) {
            log.error("Failed to publish {} notification for incident {}",
                    notification.eventType(), notification.incidentId(), ex);
        }
    }

    private static MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    static String truncate(String subject) {
        // SNS subjects must be ASCII without line breaks and at most 100 characters.
        String sanitized = subject.replaceAll("[^\\x20-\\x7E]", "?");
        return sanitized.length() <= SNS_SUBJECT_LIMIT
                ? sanitized
                : sanitized.substring(0, SNS_SUBJECT_LIMIT - 3) + "...";
    }
}
