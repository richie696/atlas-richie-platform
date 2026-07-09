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
package com.richie.component.redis.streammq.support;

import com.richie.component.cache.local.config.LocalCacheAutoConfiguration;
import com.richie.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import com.richie.component.redis.streammq.StreamMQ;
import com.richie.component.redis.streammq.config.monitor.RedisStreamMonitoringAutoConfiguration;
import com.richie.component.redis.streammq.config.stream.RedisStreamAutoConfiguration;
import com.richie.component.redis.streammq.config.stream.RedisStreamIdempotencyProperties;
import com.richie.component.redis.streammq.config.stream.RedisStreamProperties;
import com.richie.component.redis.streammq.config.tracing.RedisStreamTracingAutoConfiguration;
import com.richie.component.redis.streammq.manage.RedisStreamManager;
import com.richie.component.redis.streammq.stream.RedisStreamReactor;
import com.richie.component.redis.streammq.tracing.RedisStreamTracingUtils;
import com.richie.component.redis.streammq.utils.DeadLetterQueueUtil;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties({
        RedisStreamProperties.class,
        RedisStreamIdempotencyProperties.class
})
@Import({
        RedisBaseAutoConfiguration.class,
        LocalCacheAutoConfiguration.class,
        RedisStreamAutoConfiguration.class,
        RedisStreamMonitoringAutoConfiguration.class,
        RedisStreamTracingAutoConfiguration.class,
        StreamMQ.class,
        RedisStreamManager.class,
        RedisStreamReactor.class,
        DeadLetterQueueUtil.class,
        RedisStreamTracingUtils.class,
})
@ComponentScan(
        basePackages = "com.richie.component.cache",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.richie.component.cache.redis.manage.RedisNotificationManager.class
        )
)
public class StreammqIntegrationTestConfiguration {
}
