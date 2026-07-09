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
package com.richie.component.microservice.config.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties;
import org.springframework.context.annotation.Primary;

/**
 * Feign客户端Okhttp配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-13 10:53:00
 */
@Data
@Primary
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "spring.cloud.openfeign.httpclient")
public class FeignClientOkhttpProperties extends FeignHttpClientProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public FeignClientOkhttpProperties() {
    }

    /** OkHttp 扩展配置（读/写/连接/调用超时、日志级别、缓存、SSL 等） */
    private OkhttpExtension okHttp = new OkhttpExtension();

}
