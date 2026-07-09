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
package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * 自定义返回配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:48:53
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.custom-return")
public class CustomReturnConfig implements Serializable {

    /**
     * 安全过滤器的封禁时间单位（默认：小时）
     */
    private HttpStatus status = HttpStatus.FORBIDDEN;

    /**
     * IP黑名单的缓存路径
     */
    private String errorMessage = "请求过于频繁，请稍后再试。";

}
