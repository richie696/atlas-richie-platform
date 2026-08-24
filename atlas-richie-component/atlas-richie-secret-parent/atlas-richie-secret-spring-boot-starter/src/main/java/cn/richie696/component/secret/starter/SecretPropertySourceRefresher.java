/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.catalog.SecretBinding;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 轮询完整 Secret Bundle，并以两阶段协议驱动业务组件原子刷新。
 */
public final class SecretPropertySourceRefresher implements SmartLifecycle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SecretPropertySourceRefresher.class);
    private static final String SECRET_PREFIX = BootstrapSecretProperties.PREFIX + ".";

    private final SecretBootstrapState state;
    private final ConfigurableEnvironment environment;
    private final ObjectProvider<SecretRefreshParticipant> participants;
    private final ApplicationEventPublisher eventPublisher;
    private final SecretRefreshDiagnostics diagnostics;
    private final AtomicReference<SecretBootstrapResult> current;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object refreshMonitor = new Object();
    private volatile ScheduledExecutorService scheduler;

    public SecretPropertySourceRefresher(
            SecretBootstrapState state,
            ConfigurableEnvironment environment,
            ObjectProvider<SecretRefreshParticipant> participants,
            ApplicationEventPublisher eventPublisher) {
        this(state, environment, participants, eventPublisher, new SecretRefreshDiagnostics());
    }

    public SecretPropertySourceRefresher(
            SecretBootstrapState state,
            ConfigurableEnvironment environment,
            ObjectProvider<SecretRefreshParticipant> participants,
            ApplicationEventPublisher eventPublisher,
            SecretRefreshDiagnostics diagnostics) {
        this.state = state;
        this.environment = environment;
        this.participants = participants;
        this.eventPublisher = eventPublisher;
        this.diagnostics = diagnostics == null ? new SecretRefreshDiagnostics() : diagnostics;
        this.current = new AtomicReference<>(state.result());
    }

    @Override
    public void start() {
        BootstrapSecretProperties.Refresh refresh = state.properties().getRefresh();
        if (!refresh.isEnabled() || state.request() == null || state.catalogs() == null
                || state.propertySourceName() == null || !running.compareAndSet(false, true)) {
            return;
        }
        Duration initialDelay = positive(refresh.getInitialDelay(), "initial-delay");
        Duration interval = positive(refresh.getInterval(), "interval");
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "atlas-richie-secret-refresh");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                this::refreshSafely,
                initialDelay.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 立即执行一次刷新。返回 {@code true} 表示发布了新版本，版本未变化返回 {@code false}。
     */
    public boolean refreshNow() {
        diagnostics.recordAttempt();
        try {
            return refreshLocked();
        } catch (RuntimeException failure) {
            diagnostics.recordFailure(failure);
            throw failure;
        }
    }

    private boolean refreshLocked() {
        synchronized (refreshMonitor) {
            SecretBootstrapResult previous = current.get();
            SecretBootstrapResult candidate = state.client().load(state.request());
            if (candidate == null) {
                throw new SecretBootstrapException("SEC-PROVIDER-001", "Secret Provider returned no refresh snapshot");
            }
            if (previous.providerId().equals(candidate.providerId())
                    && previous.version().equals(candidate.version())) {
                return false;
            }

            Map<String, Object> filtered = filterAndValidate(candidate.values());
            SecretSnapshotChangedEvent metadata = new SecretSnapshotChangedEvent(
                    previous.providerId(),
                    previous.version(),
                    candidate.providerId(),
                    candidate.version(),
                    Instant.now());
            PropertySource<?> oldSource = environment.getPropertySources().get(state.propertySourceName());
            if (oldSource == null) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003", "Managed Secret PropertySource is no longer present");
            }

            MapPropertySource candidateSource = new MapPropertySource(
                    state.propertySourceName(), filtered);
            ConfigurableEnvironment candidateEnvironment = candidateEnvironment(candidateSource);
            List<PreparedSecretRefresh> prepared = new ArrayList<>();
            try {
                participants.orderedStream()
                        .map(participant -> participant.prepare(candidateEnvironment, metadata))
                        .filter(java.util.Objects::nonNull)
                        .forEach(prepared::add);
                prepared.forEach(PreparedSecretRefresh::commit);
                environment.getPropertySources().replace(state.propertySourceName(), candidateSource);
            } catch (RuntimeException failure) {
                rollback(prepared);
                if (environment.getPropertySources().get(state.propertySourceName()) != oldSource) {
                    environment.getPropertySources().replace(state.propertySourceName(), oldSource);
                }
                throw new SecretBootstrapException(
                        "SEC-REFRESH-001",
                        "Secret refresh candidate was rejected; last good snapshot remains active",
                        failure);
            }

            current.set(candidate);
            for (PreparedSecretRefresh refresh : prepared) {
                try {
                    refresh.complete();
                } catch (RuntimeException cleanupFailure) {
                    log.warn("Secret refresh committed but retired resource cleanup failed", cleanupFailure);
                }
            }
            eventPublisher.publishEvent(metadata);
            diagnostics.recordSuccess();
            log.info("Secret snapshot refreshed: provider={}, version={}",
                    candidate.providerId(), candidate.version());
            return true;
        }
    }

    private ConfigurableEnvironment candidateEnvironment(MapPropertySource candidateSource) {
        SecretCandidateEnvironment candidate = new SecretCandidateEnvironment(environment);
        candidate.getPropertySources().replace(state.propertySourceName(), candidateSource);
        return candidate;
    }

    private Map<String, Object> filterAndValidate(Map<String, Object> values) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Optional<SecretBinding> binding = state.catalogs().propertySourceBinding(entry.getKey());
            if (binding.isEmpty() || entry.getKey().startsWith(SECRET_PREFIX)) {
                continue;
            }
            Integer maxLength = binding.get().maxLength();
            int length = entry.getValue() instanceof byte[] bytes
                    ? bytes.length : String.valueOf(entry.getValue()).length();
            if (maxLength != null && length > maxLength) {
                throw new SecretConfigurationException(
                        "SEC-BOOT-003", "Secret exceeds declared maximum length for property " + entry.getKey());
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        for (SecretBinding required : state.catalogs().requiredBindings(environment)) {
            if (!filtered.containsKey(required.property())) {
                if (canUseLocalValue(required)) {
                    continue;
                }
                throw new SecretBootstrapException(
                        "SEC-STORE-001", "Required Secret is missing for property " + required.property());
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    private boolean canUseLocalValue(SecretBinding binding) {
        BootstrapSecretProperties.PropertySource source = state.properties().getPropertySource();
        return source.getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL
                && source.isLocalFallback()
                && Boolean.TRUE.equals(binding.allowLocalWhenEnabled())
                && hasLocalProperty(binding.property());
    }

    private boolean hasLocalProperty(String property) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!state.propertySourceName().equals(source.getName())
                    && source.containsProperty(property)) {
                return true;
            }
        }
        return false;
    }

    private void rollback(List<PreparedSecretRefresh> prepared) {
        List<PreparedSecretRefresh> reverse = new ArrayList<>(prepared);
        Collections.reverse(reverse);
        for (PreparedSecretRefresh refresh : reverse) {
            try {
                refresh.rollback();
            } catch (RuntimeException rollbackFailure) {
                log.error("Secret refresh participant rollback failed", rollbackFailure);
            }
        }
    }

    private void refreshSafely() {
        try {
            refreshNow();
        } catch (RuntimeException failure) {
            log.warn("Secret refresh failed; keeping the last good snapshot: failureType={}",
                    failure.getClass().getName());
        }
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new SecretConfigurationException("SEC-BOOT-003", "Secret refresh " + name + " must be positive");
        }
        return value;
    }

    @Override
    public void stop() {
        running.set(false);
        ScheduledExecutorService executor = scheduler;
        scheduler = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public void close() {
        stop();
    }
}
