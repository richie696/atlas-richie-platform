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
package cn.richie696.component.mqtt.support;

import cn.richie696.testing.redis.RedisContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public final class MqttRedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final String UNAVAILABLE_MESSAGE =
            "MQTT Redis 集成测试需要 Docker。CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final RedisContainerSupport DELEGATE = RedisContainerSupport.resolve(
            REDIS_IMAGE,
            15,
            UNAVAILABLE_MESSAGE,
            "MQTT");

    private MqttRedisIntegrationTestSupport() {
    }

    public static MqttRedisIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public void registerRedisProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            registry.add(pair.substring(0, eq), () -> pair.substring(eq + 1));
        });
    }

    void appendPropertyPairs(List<String> pairs) {
        int before = pairs.size();
        DELEGATE.appendConnectionPropertyPairs(pairs);
        // AtlasRedisProperties 绑定前缀为 platform.component.cache.redis，
        // 而 DELEGATE 只输出 spring.data.redis.*，需同时映射到组件前缀。
        // 复用 atlas-richie-component-cache 中 RedisIntegrationTestSupport 的模式。
        for (int i = before; i < pairs.size(); i++) {
            String pair = pairs.get(i);
            if (pair.startsWith("spring.data.redis.")) {
                String suffix = pair.substring("spring.data.redis.".length());
                pairs.add("platform.component.cache.redis." + suffix);
            }
        }
        pairs.add("platform.cache.cache-provider=REDIS");
        pairs.add("spring.data.redis.enable-l2-caching=false");
        pairs.add("spring.data.local.provider=CAFFEINE");
    }

    private static final class Holder {
        private static final MqttRedisIntegrationTestSupport INSTANCE = new MqttRedisIntegrationTestSupport();
    }
}
