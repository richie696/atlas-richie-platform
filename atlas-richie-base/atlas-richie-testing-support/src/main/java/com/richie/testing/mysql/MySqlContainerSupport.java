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
package com.richie.testing.mysql;

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
 * MySQL 集测连接解析：默认 Testcontainers，外部实例需显式 opt-in。
 */
public final class MySqlContainerSupport {

    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "it_pass";
    private static final String DEFAULT_DATABASE = "it_storage_local";

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String skipReason;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    @SuppressWarnings("resource")
    private final GenericContainer<?> container;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private MySqlContainerSupport(
            String[] envPrefixes,
            DockerImageName image,
            String unavailableMessage,
            ContainerMode mode,
            String skipReason,
            String jdbcUrl,
            String username,
            String password,
            GenericContainer<?> container) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.skipReason = skipReason;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.container = container;
    }

    public static MySqlContainerSupport resolve(
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

        if (preferExternal(envPrefixes)) {
            MySqlContainerSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            if (IntegrationTestPolicy.useExternal(envPrefixes) || hasExplicitConfig(envPrefixes)) {
                throw new IllegalStateException(
                        "IT_USE_EXTERNAL=true but no MySQL connection configured. "
                                + "Set MYSQL_IT_JDBC_URL or MYSQL_IT_HOST/PORT.");
            }
        }

        if (dockerAvailable) {
            return startTestcontainers(image, unavailableMessage, envPrefixes);
        }

        return new MySqlContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.UNAVAILABLE,
                unavailableMessage,
                null, null, null, null);
    }

    public boolean isAvailable() {
        return mode != ContainerMode.UNAVAILABLE;
    }

    public boolean isExternal() {
        return mode == ContainerMode.EXTERNAL;
    }

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            pairs.add("spring.datasource.url=" + jdbcUrl);
        }
        if (username != null) {
            pairs.add("spring.datasource.username=" + username);
        }
        if (password != null) {
            pairs.add("spring.datasource.password=" + password);
        }
        pairs.add("spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver");
        return pairs;
    }

    /** 连接本机已有 MySQL（如 docker-compose 映射端口）。 */
    public static MySqlContainerSupport externalConnection(
            String host,
            int port,
            String database,
            String username,
            String password,
            String unavailableMessage,
            String... envPrefixes) {
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
        return new MySqlContainerSupport(
                envPrefixes,
                imagePlaceholder(),
                unavailableMessage,
                ContainerMode.EXTERNAL,
                null,
                jdbcUrl,
                username,
                password,
                null);
    }

    @SuppressWarnings("resource")
    private static MySqlContainerSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> mysql = new GenericContainer<>(image)
                .withExposedPorts(3306)
                .withEnv("MYSQL_ROOT_PASSWORD", DEFAULT_PASSWORD)
                .withEnv("MYSQL_DATABASE", DEFAULT_DATABASE)
                .waitingFor(Wait.forListeningPort());
        mysql.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));

        String host = mysql.getHost();
        int port = mysql.getMappedPort(3306);
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + DEFAULT_DATABASE
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";

        return new MySqlContainerSupport(
                envPrefixes,
                image,
                unavailableMessage,
                ContainerMode.TESTCONTAINERS,
                null,
                jdbcUrl,
                DEFAULT_USER,
                DEFAULT_PASSWORD,
                mysql);
    }

    private static MySqlContainerSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String jdbcUrl = firstEnv(envPrefixes, "JDBC_URL", "jdbc.url");
        if (jdbcUrl != null) {
            return new MySqlContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    jdbcUrl,
                    firstEnv(envPrefixes, "USERNAME", "username", DEFAULT_USER),
                    firstEnv(envPrefixes, "PASSWORD", "password", DEFAULT_PASSWORD),
                    null);
        }

        String host = firstEnv(envPrefixes, "HOST", "host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "PORT", "port", "3306"));
            String database = firstEnv(envPrefixes, "DATABASE", "database", DEFAULT_DATABASE);
            String user = firstEnv(envPrefixes, "USERNAME", "username", DEFAULT_USER);
            String pass = firstEnv(envPrefixes, "PASSWORD", "password", DEFAULT_PASSWORD);
            String builtUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
            return new MySqlContainerSupport(
                    envPrefixes,
                    imagePlaceholder(),
                    unavailableMessage,
                    ContainerMode.EXTERNAL,
                    null,
                    builtUrl,
                    user,
                    pass,
                    null);
        }
        return null;
    }

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix, String... defaults) {
        List<String> envKeys = new ArrayList<>();
        List<String> propertyKeys = new ArrayList<>();
        for (String prefix : envPrefixes) {
            envKeys.add(prefix + "_IT_MYSQL_" + suffix);
            propertyKeys.add(toPropertyKey(prefix) + ".it.mysql." + propertySuffix);
        }
        envKeys.add("MYSQL_IT_" + suffix);
        propertyKeys.add("mysql.it." + propertySuffix);
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
        return DockerImageName.parse("mysql:8.4.7");
    }

    private static boolean preferExternal(String[] envPrefixes) {
        return IntegrationTestPolicy.useExternal(envPrefixes)
                || hasExplicitConfig(envPrefixes);
    }

    private static boolean hasExplicitConfig(String[] envPrefixes) {
        return firstEnv(envPrefixes, "JDBC_URL", "jdbc.url") != null
                || firstEnv(envPrefixes, "HOST", "host") != null;
    }
}
