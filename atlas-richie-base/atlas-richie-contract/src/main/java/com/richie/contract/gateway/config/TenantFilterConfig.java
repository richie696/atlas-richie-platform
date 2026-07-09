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
package com.richie.contract.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 租户过滤器配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:52:24
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.contract.tenant")
public class TenantFilterConfig {

    /** 默认构造函数，供配置绑定使用。 */
    public TenantFilterConfig() {
    }

    /**
     * 是否启用租户过滤器（默认：false）
     */
    private boolean enable = false;

}
