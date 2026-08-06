/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.web.jetty.management;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JettyThreadPoolUpdaterTest {

    private QueuedThreadPool threadPool;
    private Server server;
    private JettyThreadPoolUpdater updater;

    @BeforeEach
    void setUp() {
        threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(200);
        threadPool.setMinThreads(8);
        threadPool.setIdleTimeout(60_000);

        server = mock(Server.class);
        when(server.getThreadPool()).thenReturn(threadPool);

        updater = new JettyThreadPoolUpdater(server);
    }

    private StandardEnvironment envWith(String key, String value) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put(key, value);
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        env.setConversionService(new ApplicationConversionService());
        return env;
    }

    private StandardEnvironment envWithAll(Map<String, String> entries) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>(entries);
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        env.setConversionService(new ApplicationConversionService());
        return env;
    }

    @Test
    void refresh_noEnvironment_noChange() {
        int oldMax = threadPool.getMaxThreads();
        int oldMin = threadPool.getMinThreads();

        updater.refresh();

        assertThat(threadPool.getMaxThreads()).isEqualTo(oldMax);
        assertThat(threadPool.getMinThreads()).isEqualTo(oldMin);
    }

    @Test
    void refresh_environmentMaxChange_updatesPool() {
        updater.setEnvironment(envWith("server.jetty.threads.max", "300"));

        updater.refresh();

        assertThat(threadPool.getMaxThreads()).isEqualTo(300);
    }

    @Test
    void refresh_environmentMinChange_updatesPool() {
        updater.setEnvironment(envWith("server.jetty.threads.min", "16"));

        updater.refresh();

        assertThat(threadPool.getMinThreads()).isEqualTo(16);
    }

    @Test
    void refresh_environmentIdleTimeoutChange_updatesPool() {
        updater.setEnvironment(envWith("server.jetty.threads.idle-timeout", "PT2M"));

        updater.refresh();

        assertThat(threadPool.getIdleTimeout()).isEqualTo(120_000);
    }

    @Test
    void refresh_allThreeChanged_updatesPool() {
        Map<String, String> props = new HashMap<>();
        props.put("server.jetty.threads.max", "256");
        props.put("server.jetty.threads.min", "32");
        props.put("server.jetty.threads.idle-timeout", "PT30S");
        updater.setEnvironment(envWithAll(props));

        updater.refresh();

        assertThat(threadPool.getMaxThreads()).isEqualTo(256);
        assertThat(threadPool.getMinThreads()).isEqualTo(32);
        assertThat(threadPool.getIdleTimeout()).isEqualTo(30_000);
    }

    @Test
    void refresh_nonQueuedThreadPool_noChange() {
        org.eclipse.jetty.util.thread.ThreadPool nonQtp = mock(org.eclipse.jetty.util.thread.ThreadPool.class);
        Server server2 = mock(Server.class);
        when(server2.getThreadPool()).thenReturn(nonQtp);
        JettyThreadPoolUpdater updater2 = new JettyThreadPoolUpdater(server2);
        updater2.setEnvironment(envWith("server.jetty.threads.max", "999"));

        updater2.refresh();
    }

    @Test
    void refreshExplicit_positiveValues_updatesPool() {
        // Refresh must happen BEFORE threadPool.start()
        updater.refresh(512, 64, Duration.ofMinutes(5));

        assertThat(threadPool.getMaxThreads()).isEqualTo(512);
        assertThat(threadPool.getMinThreads()).isEqualTo(64);
        assertThat(threadPool.getIdleTimeout()).isEqualTo(300_000);
    }

    @Test
    void refreshExplicit_nonPositive_skipsChange() {
        int oldMax = threadPool.getMaxThreads();
        int oldMin = threadPool.getMinThreads();
        int oldIdle = threadPool.getIdleTimeout();

        updater.refresh(-1, 0, null);

        assertThat(threadPool.getMaxThreads()).isEqualTo(oldMax);
        assertThat(threadPool.getMinThreads()).isEqualTo(oldMin);
        assertThat(threadPool.getIdleTimeout()).isEqualTo(oldIdle);
    }

    @Test
    void refreshExplicit_negativeDuration_skipsChange() {
        int oldIdle = threadPool.getIdleTimeout();

        updater.refresh(0, 0, Duration.ofSeconds(-5));

        assertThat(threadPool.getIdleTimeout()).isEqualTo(oldIdle);
    }

    @Test
    void afterPropertiesSet_logsCurrentConfig() {
        updater.afterPropertiesSet();
    }
}