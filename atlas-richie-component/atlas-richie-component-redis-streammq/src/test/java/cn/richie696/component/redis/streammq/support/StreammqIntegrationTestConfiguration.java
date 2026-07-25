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
package cn.richie696.component.redis.streammq.support;

import cn.richie696.component.cache.local.config.LocalCacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.redis.streammq.StreamMQ;
import cn.richie696.component.redis.streammq.config.monitor.RedisStreamMonitoringAutoConfiguration;
import cn.richie696.component.redis.streammq.config.stream.RedisStreamAutoConfiguration;
import cn.richie696.component.redis.streammq.config.stream.RedisStreamIdempotencyProperties;
import cn.richie696.component.redis.streammq.config.stream.RedisStreamProperties;
import cn.richie696.component.redis.streammq.config.tracing.RedisStreamTracingAutoConfiguration;
import cn.richie696.component.redis.streammq.manage.RedisStreamManager;
import cn.richie696.component.redis.streammq.stream.RedisStreamReactor;
import cn.richie696.component.redis.streammq.tracing.RedisStreamTracingUtils;
import cn.richie696.component.redis.streammq.utils.DeadLetterQueueUtil;
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
        basePackages = "cn.richie696.component.cache",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = cn.richie696.component.cache.redis.manage.RedisNotificationManager.class
        )
)
public class StreammqIntegrationTestConfiguration {
}
