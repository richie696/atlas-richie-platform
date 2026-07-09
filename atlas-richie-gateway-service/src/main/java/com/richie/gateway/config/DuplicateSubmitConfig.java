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
package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 防重复提交配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-28
 */
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.duplicate-submit")
public class DuplicateSubmitConfig {

    /**
     * 是否启用防重复提交
     */
    private boolean enabled = false;

    /**
     * 需要防重复提交的路径模式
     * 支持Ant风格路径匹配
     */
    private String[] includePaths;

    /**
     * 不需要防重复提交的路径模式
     * 支持Ant风格路径匹配
     */
    private String[] excludePaths;

    /**
     * 防重复提交的时间窗口（毫秒）
     * 默认3秒内不允许重复提交
     */
    private long timeWindow = 3000;

    /**
     * 缓存过期时间（毫秒）
     * 建议设置为timeWindow的2-3倍
     */
    private long cacheExpire = 10000;

    /**
     * 是否启用请求体哈希校验
     * 如果启用，相同请求体在时间窗口内也会被拦截
     */
    private boolean enableBodyHash = true;

    /**
     * 是否启用用户级别防重复提交
     * 如果启用，同一用户在同一时间窗口内不能重复提交
     */
    private boolean enableUserLevel = true;

    /**
     * 是否启用IP级别防重复提交
     * 如果启用，同一IP在同一时间窗口内不能重复提交
     */
    private boolean enableIpLevel = true;

    /**
     * 错误消息
     */
    private String errorMessage = "请求过于频繁，请稍后再试";

    /**
     * 错误码
     */
    private String errorCode = "DUPLICATE_SUBMIT";
}
