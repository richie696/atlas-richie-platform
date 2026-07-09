/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.support;

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
 * PostgreSQL 集测连接解析：委托 Testcontainers {@link GenericContainer}，
 * 遵循 {@code MySqlContainerSupport} 模式。
 *
 * <p>默认启动 {@code postgres:16-alpine} 容器；外部实例需显式 opt-in
 * （设置 {@code TENANT_IT_PG_JDBC_URL} 或 {@code PG_IT_JDBC_URL}）。</p>
 */
public final class PostgresTestSupport {

    private static final String DEFAULT_USER = "tenant_it";
    private static final String DEFAULT_PASSWORD = "tenant_it";
    private static final String DEFAULT_DATABASE = "tenant_it";

    private final String[] envPrefixes;
    private final DockerImageName image;
    private final String unavailableMessage;

    private final ContainerMode mode;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private PostgresTestSupport(
            String[] envPrefixes,
            DockerImageName image,
            String unavailableMessage,
            ContainerMode mode,
            String jdbcUrl,
            String username,
            String password) {
        this.envPrefixes = envPrefixes;
        this.image = image;
        this.unavailableMessage = unavailableMessage;
        this.mode = mode;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static PostgresTestSupport resolve(
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
            PostgresTestSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            if (IntegrationTestPolicy.useExternal(envPrefixes) || hasExplicitConfig(envPrefixes)) {
                throw new IllegalStateException(
                        "IT_USE_EXTERNAL=true but no PostgreSQL connection configured. "
                                + "Set TENANT_IT_PG_JDBC_URL or PG_IT_JDBC_URL.");
            }
        }

        if (dockerAvailable) {
            return startTestcontainers(image, unavailableMessage, envPrefixes);
        }

        return new PostgresTestSupport(
                envPrefixes, image, unavailableMessage,
                ContainerMode.UNAVAILABLE, null, null, null);
    }

    public static PostgresTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    /** JUnit {@code @EnabledIf} 入口。 */
    public static boolean isEnabled() {
        return Holder.INSTANCE.isAvailable();
    }

    public boolean isAvailable() {
        return mode != ContainerMode.UNAVAILABLE;
    }

    public boolean isExternal() {
        return mode == ContainerMode.EXTERNAL;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** 供 {@link TenantIntegrationTestInitializer} 调用，注入 Spring 属性。 */
    public void appendPropertyPairs(List<String> pairs) {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            pairs.add("spring.datasource.url=" + jdbcUrl);
        }
        if (username != null) {
            pairs.add("spring.datasource.username=" + username);
        }
        if (password != null) {
            pairs.add("spring.datasource.password=" + password);
        }
        pairs.add("spring.datasource.driver-class-name=org.postgresql.Driver");
    }

    // ==================== Testcontainers 启动 ====================

    @SuppressWarnings("resource")
    private static PostgresTestSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> pg = new GenericContainer<>(image)
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", DEFAULT_DATABASE)
                .withEnv("POSTGRES_USER", DEFAULT_USER)
                .withEnv("POSTGRES_PASSWORD", DEFAULT_PASSWORD)
                .waitingFor(Wait.forListeningPort());
        pg.start();
        Runtime.getRuntime().addShutdownHook(new Thread(pg::stop));

        String host = pg.getHost();
        int port = pg.getMappedPort(5432);
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + DEFAULT_DATABASE;

        return new PostgresTestSupport(
                envPrefixes, image, unavailableMessage,
                ContainerMode.TESTCONTAINERS, jdbcUrl, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    // ==================== 外部实例解析 ====================

    private static PostgresTestSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String jdbcUrl = firstEnv(envPrefixes, "JDBC_URL", "jdbc.url");
        if (jdbcUrl != null) {
            return new PostgresTestSupport(
                    envPrefixes, imagePlaceholder(), unavailableMessage,
                    ContainerMode.EXTERNAL, jdbcUrl,
                    firstEnv(envPrefixes, "USERNAME", "username", DEFAULT_USER),
                    firstEnv(envPrefixes, "PASSWORD", "password", DEFAULT_PASSWORD));
        }

        String host = firstEnv(envPrefixes, "HOST", "host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "PORT", "port", "5432"));
            String database = firstEnv(envPrefixes, "DATABASE", "database", DEFAULT_DATABASE);
            String user = firstEnv(envPrefixes, "USERNAME", "username", DEFAULT_USER);
            String pass = firstEnv(envPrefixes, "PASSWORD", "password", DEFAULT_PASSWORD);
            String builtUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            return new PostgresTestSupport(
                    envPrefixes, imagePlaceholder(), unavailableMessage,
                    ContainerMode.EXTERNAL, builtUrl, user, pass);
        }
        return null;
    }

    // ==================== 辅助方法 ====================

    private static String firstEnv(String[] envPrefixes, String suffix, String propertySuffix, String... defaults) {
        List<String> envKeys = new ArrayList<>();
        List<String> propertyKeys = new ArrayList<>();
        for (String prefix : envPrefixes) {
            envKeys.add(prefix + "_IT_PG_" + suffix);
            propertyKeys.add(toPropertyKey(prefix) + ".it.pg." + propertySuffix);
        }
        envKeys.add("PG_IT_" + suffix);
        propertyKeys.add("pg.it." + propertySuffix);
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
        return DockerImageName.parse("postgres:16-alpine");
    }

    private static boolean preferExternal(String[] envPrefixes) {
        return IntegrationTestPolicy.useExternal(envPrefixes)
                || hasExplicitConfig(envPrefixes);
    }

    private static boolean hasExplicitConfig(String[] envPrefixes) {
        return firstEnv(envPrefixes, "JDBC_URL", "jdbc.url") != null
                || firstEnv(envPrefixes, "HOST", "host") != null;
    }

    // ==================== 单例 Holder ====================

    private static final DockerImageName PG_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final String UNAVAILABLE_MESSAGE =
            "Tenant 集成测试需要 Docker（Testcontainers PostgreSQL）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。本机已有 PostgreSQL 时可设 "
                    + "IT_USE_EXTERNAL=true，参见 atlas-richie-testing-support/README.md";

    private static final class Holder {
        private static final PostgresTestSupport INSTANCE = PostgresTestSupport.resolve(
                PG_IMAGE, UNAVAILABLE_MESSAGE, "TENANT");
    }
}
