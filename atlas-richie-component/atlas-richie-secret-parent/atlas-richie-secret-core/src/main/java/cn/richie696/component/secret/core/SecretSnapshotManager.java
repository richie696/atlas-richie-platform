/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.api.SecretSnapshotListener;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 以单次原子操作发布完整 Secret 快照。
 */
public final class SecretSnapshotManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SecretSnapshotManager.class);

    private final AtomicReference<SecretRuntimeSnapshot> current = new AtomicReference<>();
    private final List<SecretSnapshotListener> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> listenerFailureHandler;

    public SecretSnapshotManager() {
        this(failure -> { });
    }

    public SecretSnapshotManager(Consumer<RuntimeException> listenerFailureHandler) {
        this.listenerFailureHandler = listenerFailureHandler == null ? failure -> { } : listenerFailureHandler;
    }

    public Optional<SecretRuntimeSnapshot> current() {
        return Optional.ofNullable(current.get());
    }

    /**
     * 注册快照切换监听器。返回的句柄用于取消注册，不创建线程。
     */
    public AutoCloseable addListener(SecretSnapshotListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public synchronized void replace(SecretRuntimeSnapshot next) {
        if (next == null) {
            throw new IllegalArgumentException("next snapshot must not be null");
        }
        SecretRuntimeSnapshot previous = current.getAndSet(next);
        try {
            SecretSnapshotChangedEvent event = new SecretSnapshotChangedEvent(
                    previous == null ? null : previous.providerId(),
                    previous == null ? null : previous.version(),
                    next.providerId(),
                    next.version(),
                    Instant.now());
            notifyListeners(event);
        } finally {
            if (previous != null) {
                previous.close();
            }
        }
    }

    private void notifyListeners(SecretSnapshotChangedEvent event) {
        for (SecretSnapshotListener listener : listeners) {
            try {
                listener.onChanged(event);
            } catch (RuntimeException failure) {
                // A post-commit observer cannot roll back an already published snapshot,
                // but its failure must remain observable without exposing secret values.
                log.warn("Secret snapshot listener failed: listenerType={}, failureType={}",
                        listener.getClass().getName(), failure.getClass().getName());
                try {
                    listenerFailureHandler.accept(failure);
                } catch (RuntimeException diagnosticsFailure) {
                    log.debug("Secret snapshot listener diagnostics failed: failureType={}",
                            diagnosticsFailure.getClass().getName());
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        listeners.clear();
        SecretRuntimeSnapshot previous = current.getAndSet(null);
        if (previous != null) {
            previous.close();
        }
    }
}
