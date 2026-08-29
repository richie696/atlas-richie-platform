package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretPropertySourceRefresherTest {

    @Test
    void refreshCommitsCandidateOnlyAfterParticipantPreparation() {
        AtomicReference<String> active = new AtomicReference<>("old");
        AtomicReference<String> liveEnvironmentDuringPrepare = new AtomicReference<>();
        AtomicReference<StandardEnvironment> fixtureEnvironment = new AtomicReference<>();
        SecretRefreshParticipant participant = (environment, event) -> {
            String candidate = environment.getProperty("test.component.password");
            liveEnvironmentDuringPrepare.set(fixtureEnvironment.get().getProperty("test.component.password"));
            String previous = active.get();
            return new PreparedSecretRefresh() {
                @Override public void commit() { active.set(candidate); }
                @Override public void rollback() { active.set(previous); }
            };
        };
        TestFixture fixture = fixture(participant, "new");
        fixtureEnvironment.set(fixture.environment());

        assertThat(fixture.refresher().refreshNow()).isTrue();

        assertThat(active).hasValue("new");
        assertThat(liveEnvironmentDuringPrepare).hasValue("old");
        assertThat(fixture.environment().getProperty("test.component.password")).isEqualTo("new");
        assertThat(fixture.events()).hasSize(1);
    }

    @Test
    void failedCommitRollsBackParticipantsAndKeepsLivePropertySource() {
        AtomicReference<String> active = new AtomicReference<>("old");
        SecretRefreshParticipant participant = (environment, event) -> {
            String previous = active.get();
            return new PreparedSecretRefresh() {
                @Override public void commit() {
                    active.set("new");
                    throw new IllegalStateException("commit failed");
                }
                @Override public void rollback() { active.set(previous); }
            };
        };
        TestFixture fixture = fixture(participant, "new");

        assertThatThrownBy(fixture.refresher()::refreshNow)
                .hasMessageContaining("last good snapshot remains active");
        assertThat(active).hasValue("old");
        assertThat(fixture.environment().getProperty("test.component.password")).isEqualTo("old");
        assertThat(fixture.diagnostics().snapshot().failures()).isEqualTo(1);
    }

    @Test
    void rejectedCandidateRestoresOldPropertySourceAndParticipantState() {
        AtomicReference<String> active = new AtomicReference<>("old");
        SecretRefreshParticipant participant = (environment, event) -> {
            throw new IllegalStateException("candidate invalid");
        };
        TestFixture fixture = fixture(participant, "new");

        assertThatThrownBy(fixture.refresher()::refreshNow)
                .hasMessageContaining("last good snapshot remains active");
        assertThat(active).hasValue("old");
        assertThat(fixture.environment().getProperty("test.component.password")).isEqualTo("old");
        assertThat(fixture.events()).isEmpty();
    }

    @Test
    void listenerFailureAfterCommitDoesNotUndoOrMisreportRefresh() {
        TestFixture fixture = fixture((environment, event) -> new PreparedSecretRefresh() {
            @Override public void commit() { }
            @Override public void rollback() { }
        }, "new", true);

        assertThat(fixture.refresher().refreshNow()).isTrue();

        assertThat(fixture.environment().getProperty("test.component.password")).isEqualTo("new");
        assertThat(fixture.diagnostics().snapshot().successes()).isEqualTo(1);
        assertThat(fixture.diagnostics().snapshot().failures()).isZero();
        assertThat(fixture.diagnostics().snapshot().listenerFailures()).isEqualTo(1);
    }

    @Test
    void refreshRetainsLocalFallbackWhenRemoteRequiredBindingIsTemporarilyMissing() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "local", Map.of(
                        "test.component.password", "local-value",
                        "platform.component.test.engine", "remote")));
        String sourceName = "atlas-richie-secret[test:v1]";
        environment.getPropertySources().addFirst(new MapPropertySource(
                sourceName, Map.of("test.component.password", "old")));
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setMissingPolicy(BootstrapSecretProperties.MissingPolicy.LOCAL);
        properties.getPropertySource().setLocalFallback(true);
        var catalogs = new SecretBindingCatalogLoader().load(getClass().getClassLoader());
        SecretBootstrapRequest request = new SecretBootstrapRequest(
                "app", "test", List.of("path"), catalogs.bindings());
        SecretBootstrapResult initial = result("v1", "old");
        SecretBootstrapClient client = ignored -> new SecretBootstrapResult(
                "test", "v2", "path", Instant.now(), Map.of(), "request");
        SecretBootstrapState state = new SecretBootstrapState(
                properties, null, client, initial, request, catalogs, sourceName);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        SecretPropertySourceRefresher refresher = new SecretPropertySourceRefresher(
                state, environment, beans.getBeanProvider(SecretRefreshParticipant.class),
                event -> { });

        assertThat(refresher.refreshNow()).isTrue();
        assertThat(environment.getProperty("test.component.password")).isEqualTo("local-value");
    }

    @Test
    void oneHundredConcurrentRefreshRequestsPublishOneAtomicGeneration() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        SecretRefreshParticipant participant = (environment, event) -> new PreparedSecretRefresh() {
            @Override public void commit() { commits.incrementAndGet(); }
            @Override public void rollback() { }
        };
        TestFixture fixture = fixture(participant, "new");
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int index = 0; index < 100; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return fixture.refresher().refreshNow();
                }));
            }
            start.countDown();
            int published = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) published++;
            }

            assertThat(published).isEqualTo(1);
            assertThat(commits).hasValue(1);
            assertThat(fixture.events()).hasSize(1);
            assertThat(fixture.environment().getProperty("test.component.password")).isEqualTo("new");
            assertThat(fixture.diagnostics().snapshot().attempts()).isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }

    private TestFixture fixture(SecretRefreshParticipant participant, String nextValue) {
        return fixture(participant, nextValue, false);
    }

    private TestFixture fixture(
            SecretRefreshParticipant participant,
            String nextValue,
            boolean failingListener) {
        String sourceName = "atlas-richie-secret[test:v1]";
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                sourceName, Map.of("test.component.password", "old")));
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        var catalogs = new SecretBindingCatalogLoader().load(getClass().getClassLoader());
        SecretBootstrapRequest request = new SecretBootstrapRequest(
                "app", "test", List.of("path"), catalogs.bindings());
        SecretBootstrapResult initial = result("v1", "old");
        SecretBootstrapClient client = ignored -> result("v2", nextValue);
        SecretBootstrapState state = new SecretBootstrapState(
                properties, null, client, initial, request, catalogs, sourceName);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("participant", participant);
        List<Object> events = new java.util.ArrayList<>();
        SecretRefreshDiagnostics diagnostics = new SecretRefreshDiagnostics();
        cn.richie696.component.secret.core.SecretSnapshotManager manager =
                new cn.richie696.component.secret.core.SecretSnapshotManager(diagnostics::recordListenerFailure);
        manager.addListener(event -> {
            events.add(event);
            if (failingListener) throw new IllegalStateException("listener failed");
        });
        SecretPropertySourceRefresher refresher = new SecretPropertySourceRefresher(
                state,
                environment,
                beans.getBeanProvider(SecretRefreshParticipant.class),
                manager,
                diagnostics);
        return new TestFixture(refresher, environment, events, diagnostics);
    }

    private SecretBootstrapResult result(String version, String value) {
        return new SecretBootstrapResult(
                "test", version, "path", Instant.now(),
                Map.of("test.component.password", value), "request");
    }

    private record TestFixture(
            SecretPropertySourceRefresher refresher,
            StandardEnvironment environment,
            List<Object> events,
            SecretRefreshDiagnostics diagnostics) {
    }

}
