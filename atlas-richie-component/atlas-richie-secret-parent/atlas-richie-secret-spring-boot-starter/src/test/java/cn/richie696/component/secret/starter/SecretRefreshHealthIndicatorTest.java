/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRefreshHealthIndicatorTest {
    private static final Instant START = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void unrecoveredRecentFailureIsDegradedWithoutExposingExceptionMessages() {
        SecretRefreshDiagnostics diagnostics = new SecretRefreshDiagnostics(fixed(START));
        diagnostics.recordAttempt();
        diagnostics.recordFailure(new IllegalStateException("must-not-leak"));

        var health = new SecretRefreshHealthIndicator(
                diagnostics, properties(Duration.ofMinutes(5)), fixed(START.plusSeconds(60)))
                .health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails())
                .containsEntry("lastFailureType", IllegalStateException.class.getName())
                .doesNotContainValue("must-not-leak");
    }

    @Test
    void unrecoveredFailurePastMaxStalenessIsDownAndLaterSuccessRecovers() {
        SecretRefreshDiagnostics diagnostics = new SecretRefreshDiagnostics(fixed(START));
        diagnostics.recordFailure(new IllegalStateException());

        var stale = new SecretRefreshHealthIndicator(
                diagnostics, properties(Duration.ofMinutes(5)), fixed(START.plusSeconds(301)));
        assertThat(stale.health().getStatus()).isEqualTo(Status.DOWN);

        SecretRefreshDiagnostics recovered = new SecretRefreshDiagnostics(fixed(START.plusSeconds(302)));
        recovered.recordSuccess();
        var healthy = new SecretRefreshHealthIndicator(
                recovered, properties(Duration.ofMinutes(5)), fixed(START.plusSeconds(600)));
        assertThat(healthy.health().getStatus()).isEqualTo(Status.UP);
    }

    private BootstrapSecretProperties properties(Duration maxStaleness) {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.getRefresh().setMaxStaleness(maxStaleness);
        return properties;
    }

    private Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
