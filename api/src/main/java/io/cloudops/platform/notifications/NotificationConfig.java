package io.cloudops.platform.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
class NotificationConfig {

    /**
     * Region and credentials come from the default AWS provider chain; on ECS that is the task
     * role, so no keys are configured anywhere.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "cloudops.notifications.channel", havingValue = "sns")
    SnsClient snsClient() {
        return SnsClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "cloudops.notifications.channel", havingValue = "sns")
    IncidentNotifier snsIncidentNotifier(SnsClient snsClient, NotificationProperties properties) {
        Assert.hasText(properties.snsTopicArn(),
                "cloudops.notifications.sns-topic-arn is required when the notification channel is sns");
        return new SnsIncidentNotifier(snsClient, properties.snsTopicArn());
    }

    @Bean
    @ConditionalOnProperty(name = "cloudops.notifications.channel", havingValue = "log", matchIfMissing = true)
    IncidentNotifier loggingIncidentNotifier() {
        return new LoggingIncidentNotifier();
    }
}
