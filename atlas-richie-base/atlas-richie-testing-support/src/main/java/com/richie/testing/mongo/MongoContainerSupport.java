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
package com.richie.testing.mongo;

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
 * MongoDB 集测连接解析：默认 Testcontainers，外部实例需显式 opt-in。
 */
public final class MongoContainerSupport {

    private static final String DEFAULT_USER = "it_user";
    private static final String DEFAULT_PASSWORD = "it_pass";
    private static final String DEFAULT_DATABASE = "it_mongo";

    private final ContainerMode mode;
    private final String skipReason;
    private final String uri;
    private final String database;
    private final String username;
    private final String password;

    static {
        TestcontainersEnvironment.ensureConfigured();
    }

    private MongoContainerSupport(
            ContainerMode mode,
            String skipReason,
            String uri,
            String database,
            String username,
            String password) {
        this.mode = mode;
        this.skipReason = skipReason;
        this.uri = uri;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public static MongoContainerSupport resolve(
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
            MongoContainerSupport external = resolveExternal(envPrefixes, unavailableMessage);
            if (external != null) {
                return external;
            }
            throw new IllegalStateException(
                    "IT_USE_EXTERNAL=true but no MongoDB connection configured. "
                            + "Set MONGODB_IT_URI or MONGODB_IT_HOST/PORT.");
        }

        return new MongoContainerSupport(
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

    public boolean isTestcontainers() {
        return mode == ContainerMode.TESTCONTAINERS;
    }

    public String skipReason() {
        return skipReason;
    }

    public List<String> appendConnectionPropertyPairs(List<String> pairs) {
        if (uri != null && !uri.isBlank()) {
            pairs.add("platform.component.mongodb.uri=" + uri);
        }
        if (database != null) {
            pairs.add("platform.component.mongodb.database=" + database);
        }
        if (username != null) {
            pairs.add("platform.component.mongodb.username=" + username);
        }
        if (password != null) {
            pairs.add("platform.component.mongodb.password=" + password);
        }
        return pairs;
    }

    @SuppressWarnings("resource")
    private static MongoContainerSupport startTestcontainers(
            DockerImageName image,
            String unavailableMessage,
            String... envPrefixes) {
        GenericContainer<?> mongo = new GenericContainer<>(image)
                .withExposedPorts(27017)
                .withEnv("MONGO_INITDB_ROOT_USERNAME", DEFAULT_USER)
                .withEnv("MONGO_INITDB_ROOT_PASSWORD", DEFAULT_PASSWORD)
                .withEnv("MONGO_INITDB_DATABASE", DEFAULT_DATABASE)
                // 使用 ping 命令等待 MongoDB 完全就绪（包括 init 脚本和用户创建完成），
                // 仅依赖 forLogMessage("Waiting for connections") 可能在 init 脚本执行前就通过，
                // 导致客户端握手阶段遭遇 Connection reset。
                .waitingFor(Wait.forSuccessfulCommand(
                                "mongosh --eval 'db.runCommand(\"ping\").ok' --quiet")
                        .withStartupTimeout(java.time.Duration.ofSeconds(120)))
                .withStartupTimeout(java.time.Duration.ofSeconds(180));
        mongo.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mongo::stop));

        String host = mongo.getHost();
        int port = mongo.getMappedPort(27017);
        String uri = "mongodb://" + DEFAULT_USER + ":" + DEFAULT_PASSWORD + "@"
                + host + ":" + port + "/" + DEFAULT_DATABASE + "?authSource=admin";

        return new MongoContainerSupport(
                ContainerMode.TESTCONTAINERS,
                null,
                uri,
                DEFAULT_DATABASE,
                DEFAULT_USER,
                DEFAULT_PASSWORD);
    }

    private static MongoContainerSupport resolveExternal(
            String[] envPrefixes,
            String unavailableMessage) {
        String uri = firstEnv(envPrefixes, "URI", "uri");
        if (uri != null) {
            return new MongoContainerSupport(
                    ContainerMode.EXTERNAL,
                    null,
                    uri,
                    firstEnv(envPrefixes, "DATABASE", "database", DEFAULT_DATABASE),
                    firstEnv(envPrefixes, "USERNAME", "username"),
                    firstEnv(envPrefixes, "PASSWORD", "password"));
        }

        String host = firstEnv(envPrefixes, "HOST", "host");
        if (host != null) {
            int port = Integer.parseInt(firstEnv(envPrefixes, "PORT", "port", "27017"));
            String user = firstEnv(envPrefixes, "USERNAME", "username", DEFAULT_USER);
            String pass = firstEnv(envPrefixes, "PASSWORD", "password", DEFAULT_PASSWORD);
            String db = firstEnv(envPrefixes, "DATABASE", "database", DEFAULT_DATABASE);
            String builtUri = "mongodb://" + user + ":" + pass + "@" + host + ":" + port + "/" + db;
            return new MongoContainerSupport(
                    ContainerMode.EXTERNAL,
                    null,
                    builtUri,
                    db,
                    user,
                    pass);
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
        envKeys.add("MONGODB_IT_" + suffix);
        propertyKeys.add("mongodb.it." + propertySuffix);
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
}
