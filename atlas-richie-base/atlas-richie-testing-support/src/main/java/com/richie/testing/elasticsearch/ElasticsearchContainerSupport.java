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
package com.richie.testing.elasticsearch;

import com.richie.testing.container.ContainerMode;
import com.richie.testing.docker.TestcontainersEnvironment;
import com.richie.testing.env.IntegrationTestPolicy;
import com.richie.testing.env.TestEnv;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 集测连接解析：默认 Testcontainers，外部实例需显式 opt-in。
 */
public final class ElasticsearchContainerSupport {

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String skipReason;
    private final String hosts;
    @SuppressWarnings("resource")
    private final GenericContainer<?> container;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private ElasticsearchContainerSupport(
            String[] envPrefixes,
            DockerImageName image,
            String unavailableMessage,
            ContainerMode mode,
            String skipReason,
            String hosts,
            GenericContainer<?> container) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.skipReason = skipReason;
        this.hosts = hosts;
        this.container = container;
    }

    public static ElasticsearchContainerSupport resolve(
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
            ElasticsearchContainerSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            throw new IllegalStateException(
                    "IT_USE_EXTERNAL=true but no Elasticsearch connection configured. "
                            + "Set ELASTICSEARCH_IT_HOSTS.");
        }

        return new ElasticsearchContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.UNAVAILABLE,
                unavailableMessage,
                null,
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

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (hosts != null && !hosts.isBlank()) {
            pairs.add("platform.component.search.hosts=" + hosts);
        }
        pairs.add("platform.component.search.provider=elasticsearch");
        pairs.add("platform.component.search.elasticsearch.cluster.health-check=false");
        return pairs;
    }

    @SuppressWarnings("resource")
    private static ElasticsearchContainerSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> es = new GenericContainer<>(image)
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("xpack.security.enrollment.enabled", "false")
                .withEnv("xpack.security.http.ssl.enabled", "false")
                .withEnv("xpack.security.transport.ssl.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCode(200));
        es.start();
        Runtime.getRuntime().addShutdownHook(new Thread(es::stop));

        String hosts = "http://" + es.getHost() + ":" + es.getMappedPort(9200);
        return new ElasticsearchContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.TESTCONTAINERS,
                null,
                hosts,
                es);
    }

    private static ElasticsearchContainerSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String hosts = firstEnv(envPrefixes, "HOSTS", "hosts");
        if (hosts != null) {
            return new ElasticsearchContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    hosts,
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
        envKeys.add("ELASTICSEARCH_IT_" + suffix);
        propertyKeys.add("elasticsearch.it." + propertySuffix);
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
        return DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.1");
    }
}
