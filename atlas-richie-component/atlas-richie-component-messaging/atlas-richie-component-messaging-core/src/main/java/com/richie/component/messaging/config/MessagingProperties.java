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
package com.richie.component.messaging.config;

import com.richie.component.messaging.enums.DatasourceTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Cloud Stream 消息组件配置属性。
 * <p>
 * 配置前缀：{@code spring.cloud.stream}，用于幂等去重数据源与重试次数等。
 *
 * @author richie696
 * @since 2022-09-16
 */
@Data
@ConfigurationProperties(prefix = "spring.cloud.stream")
public class MessagingProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public MessagingProperties() {
    }

    /**
     * 幂等去重使用的数据缓存源（可选值：memory、redis）
     */
    private DatasourceTypeEnum datasource = DatasourceTypeEnum.MEMORY;

    /**
     * 消息处理失败后的最大重试次数（默认：3次）
     */
    private Integer maxRetries = 3;

}
