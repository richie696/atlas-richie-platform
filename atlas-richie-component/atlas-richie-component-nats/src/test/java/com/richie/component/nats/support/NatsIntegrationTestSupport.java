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
package com.richie.component.nats.support;

import com.richie.testing.nats.NatsServerContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

/**
 * NATS 集成测试支撑类
 *
 * <p>委托 {@link NatsServerContainerSupport} 管理 NATS Docker 容器，
 * 提供连接信息和 Spring 属性注入能力。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class NatsIntegrationTestSupport {

    private static final DockerImageName NATS_IMAGE = DockerImageName.parse("nats:2.10-alpine");
    private static final String UNAVAILABLE_MESSAGE =
            "NATS 集成测试需要 Docker（Testcontainers）。CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final NatsServerContainerSupport DELEGATE = NatsServerContainerSupport.resolve(
            NATS_IMAGE,
            UNAVAILABLE_MESSAGE,
            "NATS");

    private NatsIntegrationTestSupport() {
    }

    public static NatsIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public String connectionUrl() {
        return DELEGATE.getConnectionUrl();
    }

    public String host() {
        return DELEGATE.getHost();
    }

    public int port() {
        return DELEGATE.getPort();
    }

    /**
     * 将 NATS 连接属性注入 Spring {@link DynamicPropertyRegistry}
     */
    public void registerNatsProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            registry.add(pair.substring(0, eq), () -> pair.substring(eq + 1));
        });
    }

    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
    }

    private static final class Holder {
        private static final NatsIntegrationTestSupport INSTANCE = new NatsIntegrationTestSupport();
    }
}
