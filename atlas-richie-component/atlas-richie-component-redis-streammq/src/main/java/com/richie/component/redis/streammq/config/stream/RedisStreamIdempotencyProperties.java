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
package com.richie.component.redis.streammq.config.stream;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis Stream 消费幂等去重配置（自 cache 模块迁移）。
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis.stream-idempotency")
public class RedisStreamIdempotencyProperties {

    private String keyPrefix = "idemp:stream:";

    private Duration memoryTtl = Duration.ofHours(1);

    private Duration redisTtl = Duration.ofHours(24);
}
