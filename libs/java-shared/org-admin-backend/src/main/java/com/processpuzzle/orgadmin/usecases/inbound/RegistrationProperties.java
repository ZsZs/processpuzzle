package com.processpuzzle.orgadmin.usecases.inbound;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code processpuzzle.registration.*}. Closed by default: a deployment opts in to open sign-up.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "processpuzzle.registration")
public class RegistrationProperties {

    /** Whether {@code POST /registration} accepts requests. */
    private boolean enabled = false;

    /** Whether new accounts must verify their email address; needs SMTP configured in the realm. */
    private boolean verifyEmail = false;

    /** Attempts allowed per client address within {@link #window}. */
    private int maxAttempts = 5;

    private Duration window = Duration.ofMinutes(10);
}
