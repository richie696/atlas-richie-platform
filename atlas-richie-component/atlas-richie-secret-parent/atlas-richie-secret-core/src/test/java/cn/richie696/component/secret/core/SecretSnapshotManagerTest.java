/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SecretSnapshotManagerTest {

    @Test
    void replaceIsAtomicPublishesSanitizedMetadataAndDestroysPreviousValues() {
        SecretSnapshotManager manager = new SecretSnapshotManager();
        List<SecretSnapshotChangedEvent> events = new ArrayList<>();
        manager.addListener(events::add);
        DestroyableSecretValue firstValue = DestroyableSecretValue.ofChars("first-secret".toCharArray());
        DestroyableSecretValue secondValue = DestroyableSecretValue.ofChars("second-secret".toCharArray());
        SecretRuntimeSnapshot first = snapshot("v1", firstValue);
        SecretRuntimeSnapshot second = snapshot("v2", secondValue);

        manager.replace(first);
        manager.replace(second);

        assertThat(manager.current()).containsSame(second);
        assertThat(firstValue.destroyed()).isTrue();
        assertThat(secondValue.destroyed()).isFalse();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).previousVersion()).isEqualTo("v1");
        assertThat(events.get(1).currentVersion()).isEqualTo("v2");
        assertThat(events.get(1).toString()).doesNotContain("first-secret", "second-secret");

        manager.close();
        assertThat(secondValue.destroyed()).isTrue();
    }

    @Test
    void failingObserverCannotPreventSnapshotPublicationOrOtherObservers() {
        SecretSnapshotManager manager = new SecretSnapshotManager();
        List<String> observedVersions = new ArrayList<>();
        manager.addListener(event -> {
            throw new IllegalStateException("observer failure");
        });
        manager.addListener(event -> observedVersions.add(event.currentVersion()));
        SecretRuntimeSnapshot snapshot = snapshot(
                "v1",
                DestroyableSecretValue.ofChars("secret".toCharArray()));

        manager.replace(snapshot);

        assertThat(manager.current()).containsSame(snapshot);
        assertThat(observedVersions).containsExactly("v1");
        manager.close();
    }

    @Test
    void failingObserverIsReportedToDiagnosticsWithoutBlockingPublication() {
        AtomicInteger failures = new AtomicInteger();
        SecretSnapshotManager manager = new SecretSnapshotManager(failure -> failures.incrementAndGet());
        manager.addListener(event -> { throw new IllegalStateException("observer failure"); });

        manager.replace(snapshot("v1", DestroyableSecretValue.ofChars("secret".toCharArray())));

        assertThat(failures).hasValue(1);
        assertThat(manager.current()).isPresent();
        manager.close();
    }

    @Test
    void concurrentReplacementDoesNotClosePreviousSnapshotBeforeObserversFinish() throws Exception {
        SecretSnapshotManager manager = new SecretSnapshotManager();
        CountDownLatch observerEntered = new CountDownLatch(1);
        CountDownLatch releaseObserver = new CountDownLatch(1);
        DestroyableSecretValue firstValue = DestroyableSecretValue.ofChars("first".toCharArray());
        manager.replace(snapshot("v1", firstValue));
        manager.addListener(event -> {
            observerEntered.countDown();
            try {
                releaseObserver.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        Thread firstReplacement = new Thread(() -> manager.replace(
                snapshot("v2", DestroyableSecretValue.ofChars("second".toCharArray()))));
        firstReplacement.start();
        assertThat(observerEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread secondReplacement = new Thread(() -> manager.replace(
                snapshot("v3", DestroyableSecretValue.ofChars("third".toCharArray()))));
        secondReplacement.start();
        Thread.sleep(50);
        assertThat(firstValue.destroyed()).isFalse();

        releaseObserver.countDown();
        firstReplacement.join(2000);
        secondReplacement.join(2000);
        manager.close();
    }

    private SecretRuntimeSnapshot snapshot(String version, DestroyableSecretValue value) {
        return new SecretRuntimeSnapshot("test", version, Instant.EPOCH, Map.of("managed", value));
    }
}
