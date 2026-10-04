package io.cloudops.platform.notifications;

import io.cloudops.platform.incidents.domain.Severity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnsIncidentNotifierTest {

    private static final String TOPIC = "arn:aws:sns:eu-west-1:123456789012:cloudops-dev-incidents";

    private final SnsClient sns = mock(SnsClient.class);
    private final SnsIncidentNotifier notifier = new SnsIncidentNotifier(sns, TOPIC);

    @Test
    void publishesWithFilterableAttributes() {
        when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m-1").build());

        notifier.send(new IncidentNotification(UUID.randomUUID(), "INCIDENT_OPENED", Severity.SEV1, "Subject", "Body"));

        ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(request.capture());
        assertThat(request.getValue().topicArn()).isEqualTo(TOPIC);
        assertThat(request.getValue().messageAttributes().get("severity").stringValue()).isEqualTo("SEV1");
        assertThat(request.getValue().messageAttributes().get("eventType").stringValue()).isEqualTo("INCIDENT_OPENED");
    }

    @Test
    void deliveryFailuresDoNotPropagate() {
        when(sns.publish(any(PublishRequest.class))).thenThrow(SdkClientException.create("network down"));

        assertThatCode(() -> notifier.send(new IncidentNotification(UUID.randomUUID(), "INCIDENT_OPENED",
                Severity.SEV1, "Subject", "Body"))).doesNotThrowAnyException();
    }

    @Test
    void subjectsAreSanitizedAndTruncatedToSnsLimits() {
        assertThat(SnsIncidentNotifier.truncate("Line\nbreak é")).isEqualTo("Line?break ?");
        assertThat(SnsIncidentNotifier.truncate("x".repeat(150))).hasSize(100).endsWith("...");
    }
}
