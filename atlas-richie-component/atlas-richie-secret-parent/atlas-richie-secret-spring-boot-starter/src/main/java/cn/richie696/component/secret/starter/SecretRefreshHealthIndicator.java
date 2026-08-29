/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Refresh health based on last-good snapshot staleness, never secret content. */
public final class SecretRefreshHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");

    private final SecretRefreshDiagnostics diagnostics;
    private final Duration maxStaleness;
    private final Clock clock;

    public SecretRefreshHealthIndicator(
            SecretRefreshDiagnostics diagnostics,
            BootstrapSecretProperties properties) {
        this(diagnostics, properties, Clock.systemUTC());
    }

    SecretRefreshHealthIndicator(
            SecretRefreshDiagnostics diagnostics,
            BootstrapSecretProperties properties,
            Clock clock) {
        this.diagnostics = diagnostics;
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
        Duration configured = properties.getRefresh().getMaxStaleness();
        this.maxStaleness = configured == null || configured.isZero() || configured.isNegative()
                ? Duration.ofMinutes(5) : configured;
    }

    @Override
    public Health health() {
        SecretRefreshDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        Health.Builder builder;
        if (!snapshot.hasUnrecoveredFailure()) {
            builder = Health.up();
        } else if (isStale(snapshot.lastSuccessAt(), snapshot.lastFailureAt())) {
            builder = Health.down();
        } else {
            builder = Health.status(DEGRADED);
        }
        builder
                .withDetail("attempts", snapshot.attempts())
                .withDetail("successes", snapshot.successes())
                .withDetail("failures", snapshot.failures())
                .withDetail("listenerFailures", snapshot.listenerFailures())
                .withDetail("maxStaleness", maxStaleness.toString());
        addIfPresent(builder, "lastSuccessAt", snapshot.lastSuccessAt());
        addIfPresent(builder, "lastFailureAt", snapshot.lastFailureAt());
        addIfPresent(builder, "lastFailureType", snapshot.lastFailureType());
        return builder.build();
    }

    private boolean isStale(Instant lastSuccessAt, Instant lastFailureAt) {
        Instant baseline = lastSuccessAt == null ? lastFailureAt : lastSuccessAt;
        return baseline != null && !clock.instant().isBefore(baseline.plus(maxStaleness));
    }

    private void addIfPresent(Health.Builder builder, String name, Object value) {
        if (value != null) {
            builder.withDetail(name, value);
        }
    }
}
