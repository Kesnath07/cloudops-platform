package io.cloudops.platform.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@Configuration
@EnableAsync
public class ApplicationConfig {

    /**
     * Business timestamps come from an injected clock so tests can pin time deterministically.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
