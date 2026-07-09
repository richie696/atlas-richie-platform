/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.testing.nats;

import com.richie.testing.container.ContainerMode;
import com.richie.testing.docker.TestcontainersEnvironment;
import com.richie.testing.env.IntegrationTestPolicy;
import com.richie.testing.env.TestEnv;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * NATS Server 集测连接解析：默认 Testcontainers，外部实例需显式 opt-in。
 * <p>
 * 使用 {@code nats:2.10-alpine} 官方镜像，默认启用 JetStream（{@code -js} 参数）。
 *
 * @author richie696
 * @since 1.0.0
 */
public final class NatsServerContainerSupport {

    private static final int NATS_CLIENT_PORT = 4222;

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String skipReason;
    private final String host;
    private final int port;
    @SuppressWarnings("resource")
    private final GenericContainer<?> container;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private NatsServerContainerSupport(
            String[] envPrefixes,
            DockerImageName image,
            String unavailableMessage,
            ContainerMode mode,
            String skipReason,
            String host,
            int port,
            GenericContainer<?> container) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.skipReason = skipReason;
        this.host = host;
        this.port = port;
        this.container = container;
    }

    /**
     * 解析 NATS 容器连接：优先 Testcontainers，其次外部实例，最后 UNAVAILABLE。
     *
     * @param image              Docker 镜像名称
     * @param unavailableMessage Docker 不可用时的提示信息
     * @param envPrefixes        环境变量前缀（如 "NATS"）
     * @return NatsServerContainerSupport 实例
     */
    public static NatsServerContainerSupport resolve(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        boolean requireDocker = IntegrationTestPolicy.requireDocker(envPrefixes);
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        if (requireDocker && !dockerAvailable) {
            throw new IllegalStateException(
                    "IT_REQUIRE_DOCKER=true but Docker is not available. "
                            + "Install Docker Desktop / enable CI dind.");
        }

        if (dockerAvailable) {
            return startTestcontainers(image, unavailableMessage, envPrefixes);
        }

        if (IntegrationTestPolicy.useExternal(envPrefixes)) {
            NatsServerContainerSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            throw new IllegalStateException(
                    "IT_USE_EXTERNAL=true but no NATS server configured. "
                            + "Set NATS_IT_HOST/PORT.");
        }

        return new NatsServerContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.UNAVAILABLE,
                unavailableMessage,
                null,
                0,
                null);
    }

    public boolean isAvailable() {
        return mode != ContainerMode.UNAVAILABLE;
    }

    public boolean isExternal() {
        return mode == ContainerMode.EXTERNAL;
    }

    public String skipReason() {
        return skipReason;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * 返回 NATS 连接 URL（如 {@code nats://localhost:4222}）
     */
    public String getConnectionUrl() {
        return "nats://" + host + ":" + port;
    }

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (host != null) {
            pairs.add("platform.nats.server=" + getConnectionUrl());
        }
        pairs.add("platform.nats.enabled=true");
        return pairs;
    }

    @SuppressWarnings("resource")
    private static NatsServerContainerSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> nats = new GenericContainer<>(image)
                .withCommand("-js") // 启用 JetStream
                .withExposedPorts(NATS_CLIENT_PORT)
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1)
                        .withStartupTimeout(Duration.ofSeconds(30)));
        nats.start();
        Runtime.getRuntime().addShutdownHook(new Thread(nats::stop));

        return new NatsServerContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.TESTCONTAINERS,
                null,
                nats.getHost(),
                nats.getMappedPort(NATS_CLIENT_PORT),
                nats);
    }

    private static NatsServerContainerSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String host = firstEnv(envPrefixes, "HOST", "host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "PORT", "port", "4222"));
            return new NatsServerContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    host,
                    port,
                    null);
        }
        return null;
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix, String... defaults) {
        List<String> envKeys = new ArrayList<>();
        List<String> propertyKeys = new ArrayList<>();
        for (String prefix : envPrefixes) {
            envKeys.add(prefix + "_IT_" + suffix);
            propertyKeys.add(toPropertyKey(prefix) + ".it." + propertySuffix);
        }
        envKeys.add("NATS_IT_" + suffix);
        propertyKeys.add("nats.it." + propertySuffix);
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
        return DockerImageName.parse("nats:2.10-alpine");
    }
}
