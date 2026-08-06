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
package cn.richie696.component.messaging.enums.support;

import cn.richie696.component.cache.config.CacheAutoConfiguration;
import cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration;
import cn.richie696.component.messaging.config.MessagingProperties;
import cn.richie696.component.messaging.filter.datasource.impl.MemoryDatasourceHandlerImpl;
import cn.richie696.component.messaging.filter.datasource.impl.RedisDatasourceHandlerImpl;
import cn.richie696.component.messaging.filter.handler.impl.MessageHandlerServiceImpl;
import cn.richie696.context.common.api.SpringContextHolder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration"
})
@EnableConfigurationProperties(MessagingProperties.class)
@Import({
        CacheAutoConfiguration.class,
        RedisBaseAutoConfiguration.class,
        SpringContextHolder.class,
        MemoryDatasourceHandlerImpl.class,
        RedisDatasourceHandlerImpl.class,
        MessageHandlerServiceImpl.class,
})
public class MessagingIntegrationTestConfiguration {
}
