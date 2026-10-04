package io.cloudops.platform.notifications;

import io.cloudops.platform.incidents.domain.Severity;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param channel          where notifications go: {@code log} for local development, {@code sns} in AWS
 * @param snsTopicArn      topic receiving incident notifications when the channel is {@code sns}
 * @param minimumSeverity  incidents below this severity do not page anyone
 */
@ConfigurationProperties("cloudops.notifications")
public record NotificationProperties(
        @DefaultValue("log") Channel channel,
        String snsTopicArn,
        @DefaultValue("SEV2") Severity minimumSeverity) {

    public enum Channel {
        LOG,
        SNS
    }
}
