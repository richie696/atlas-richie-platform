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
package com.richie.testing.redis;

import com.richie.testing.container.ContainerMode;
import com.richie.testing.docker.TestcontainersEnvironment;
import com.richie.testing.env.IntegrationTestPolicy;
import com.richie.testing.env.TestEnv;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Redis 集测连接解析：默认 Testcontainers，外部 Redis 需显式 opt-in。
 */
public final class RedisContainerSupport {

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final int externalDefaultDatabase;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String skipReason;
    private final String url;
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    @SuppressWarnings("resource")
    private final GenericContainer<?> container;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private RedisContainerSupport(
            String[] envPrefixes,
            DockerImageName image,
            int externalDefaultDatabase,
            String unavailableMessage,
            ContainerMode mode,
            String skipReason,
            String url,
            String host,
            int port,
            String password,
            int database,
            GenericContainer<?> container) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.externalDefaultDatabase = externalDefaultDatabase;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.skipReason = skipReason;
        this.url = url;
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.container = container;
    }

    public static RedisContainerSupport resolve(
            DockerImageName image,
            int externalDefaultDatabase,
            String unavailableMessage,
            String... envPrefixes) {
        boolean requireDocker = IntegrationTestPolicy.requireDocker(envPrefixes);
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        if (requireDocker && !dockerAvailable) {
            throw new IllegalStateException(
                    "IT_REQUIRE_DOCKER=true but Docker is not available. "
                            + "Install Docker Desktop / enable CI dind.");
        }

        if (preferExternal(envPrefixes)) {
            RedisContainerSupport external = resolveExternal(envPrefixes, externalDefaultDatabase, unavailableMessage);
            if (external != null) {
                return external;
            }
            if (IntegrationTestPolicy.useExternal(envPrefixes) || hasExplicitConfig(envPrefixes)) {
                throw new IllegalStateException(
                        "IT_USE_EXTERNAL=true but no Redis connection configured. "
                                + "Set REDIS_IT_HOST/PORT/PASSWORD or legacy CACHE_IT_REDIS_* variables.");
            }
        }

        if (dockerAvailable) {
            return startTestcontainers(image, externalDefaultDatabase, unavailableMessage, envPrefixes);
        }

        return new RedisContainerSupport(
                envPrefixes,
                image,
                externalDefaultDatabase,
                unavailableMessage,
                ContainerMode.UNAVAILABLE,
                unavailableMessage,
                null, null, 0, null, 0, null);
    }

    /** 连接本机已有 Redis（如 docker-compose 映射端口）。 */
    public static RedisContainerSupport externalConnection(
            String host,
            int port,
            String password,
            int database,
            String unavailableMessage,
            String... envPrefixes) {
        return new RedisContainerSupport(
                envPrefixes,
                imagePlaceholder(),
                database,
                unavailableMessage,
                ContainerMode.EXTERNAL,
                null,
                null,
                host,
                port,
                password,
                database,
                null);
    }

    public boolean isAvailable() {
        return mode != ContainerMode.UNAVAILABLE;
    }

    public boolean isExternal() {
        return mode == ContainerMode.EXTERNAL;
    }

    public boolean isTestcontainers() {
        return mode == ContainerMode.TESTCONTAINERS;
    }

    public String skipReason() {
        return skipReason;
    }

    public void registerRedisConnectionProperties(DynamicPropertyRegistry registry) {
        appendConnectionPropertyPairs(new ArrayList<>()).forEach(pair -> {
            int eq = pair.indexOf('=');
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            registry.add(key, () -> value);
        });
    }

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (url != null && !url.isBlank()) {
            pairs.add("spring.data.redis.url=" + url);
        } else if (host != null) {
            pairs.add("spring.data.redis.host=" + host);
            pairs.add("spring.data.redis.port=" + port);
            pairs.add("spring.data.redis.url=redis://" + host + ":" + port);
            if (password != null && !password.isBlank()) {
                pairs.add("spring.data.redis.password=" + password);
            }
        }
        pairs.add("spring.data.redis.database=" + database);
        return pairs;
    }

    private static RedisContainerSupport startTestcontainers(
            DockerImageName image,
            int externalDefaultDatabase,
            String unavailableMessage,
            String... envPrefixes) {
        @SuppressWarnings("resource")
        GenericContainer<?> redis = new GenericContainer<>(image)
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
        redis.start();
        Runtime.getRuntime().addShutdownHook(new Thread(redis::stop));
        return new RedisContainerSupport(
                envPrefixes,
                image,
                externalDefaultDatabase,
                unavailableMessage,
                ContainerMode.TESTCONTAINERS,
                null,
                null,
                redis.getHost(),
                redis.getMappedPort(6379),
                null,
                0,
                redis);
    }

    private static RedisContainerSupport resolveExternal(
            String[] envPrefixes,
            int externalDefaultDatabase,
            String unavailableMessage) {
        int database = parseExternalDatabase(envPrefixes, externalDefaultDatabase);

        String url = firstEnv(envPrefixes, "REDIS_URL", "redis.url");
        if (url != null) {
            return externalUrl(envPrefixes, imagePlaceholder(), externalDefaultDatabase, unavailableMessage, url, database);
        }

        String host = firstEnv(envPrefixes, "REDIS_HOST", "redis.host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "REDIS_PORT", "redis.port", "6379"));
            String password = firstEnv(envPrefixes, "REDIS_PASSWORD", "redis.password");
            return new RedisContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    externalDefaultDatabase,
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    null,
                    host,
                    port,
                    password,
                    database,
                    null);
        }
        return null;
    }

    private static RedisContainerSupport externalUrl(
            String[] envPrefixes,
            DockerImageName image,
            int externalDefaultDatabase,
            String unavailableMessage,
            String url,
            int database) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        String userInfo = uri.getUserInfo();
        String password = null;
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            password = colon >= 0 ? userInfo.substring(colon + 1) : userInfo;
        }
        return new RedisContainerSupport(
                envPrefixes,
                image,
                externalDefaultDatabase,
                unavailableMessage,
                ContainerMode.EXTERNAL,
                null,
                url,
                host,
                port,
                password,
                database,
                null);
    }

    private static int parseExternalDatabase(String[] envPrefixes, int externalDefaultDatabase) {
        String raw = firstEnv(
                envPrefixes,
                "REDIS_DATABASE",
                "redis.database",
                String.valueOf(externalDefaultDatabase));
        if (raw == null) {
            return externalDefaultDatabase;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return externalDefaultDatabase;
        }
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix, String... defaults) {
        List<String> envKeys = new ArrayList<>();
        List<String> propertyKeys = new ArrayList<>();
        for (String prefix : envPrefixes) {
            envKeys.add(prefix + "_IT_" + suffix);
            propertyKeys.add(toPropertyKey(prefix) + ".it." + propertySuffix);
        }
        envKeys.add("REDIS_IT_" + suffix);
        propertyKeys.add("redis.it." + propertySuffix);
        return TestEnv.firstResolved(
                envKeys.toArray(String[]::new),
                propertyKeys.toArray(String[]::new),
                defaults);
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix) {
        return firstEnv(envPrefixes, suffix, propertySuffix, new String[0]);
    }

    private static String toPropertyKey(String prefix) {
        return prefix.toLowerCase().replace('_', '.');
    }

    private static DockerImageName imagePlaceholder() {
        return DockerImageName.parse("redis:7-alpine");
    }

    private static boolean preferExternal(String[] envPrefixes) {
        return IntegrationTestPolicy.useExternal(envPrefixes)
                || hasExplicitConfig(envPrefixes);
    }

    private static boolean hasExplicitConfig(String[] envPrefixes) {
        return firstEnv(envPrefixes, "REDIS_URL", "redis.url") != null
                || firstEnv(envPrefixes, "REDIS_HOST", "redis.host") != null;
    }
}
