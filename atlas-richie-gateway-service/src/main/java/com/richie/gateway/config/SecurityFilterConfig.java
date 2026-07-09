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

import com.richie.gateway.enums.SecurityRuleEnum;
import com.richie.context.utils.data.Collections;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 安全过滤器配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:52:24
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security")
public class SecurityFilterConfig {

    /**
     * 是否启用安全过滤器（默认：false）
     */
    private boolean enable = false;

    /**
     * 安全过滤器的时间间隔单位（默认：分钟）
     */
    private TimeUnit securityTimeIntervalUnit = TimeUnit.MINUTES;

    /**
     * 安全过滤器的时间间隔值（默认：1）
     */
    private Integer securityTimeIntervalValue = 1;

    /**
     * 单个IP访问频率的安全阈值（默认：120）
     * <p style="color: yellow">
     *     说明：假如配置文件设置如下<br>
     *     {@link SecurityFilterConfig#securityTimeIntervalUnit} 为 {@link TimeUnit#MINUTES}<br>
     *     {@link SecurityFilterConfig#securityTimeIntervalValue} 为 1<br>
     *     则表示：1分钟内，单个IP访问同一个接口的次数不能超过120次，否则会触发安全过滤器的拦截规则
     */
    private Integer securityThreshold = 120;

    /**
     * 安全过滤器的拦截规则（默认：SecurityRuleEnum.BANNED_IP - 封禁IP）
     */
    private SecurityRuleEnum rule = SecurityRuleEnum.BANNED_IP;

    /**
     * 封禁策略配置
     */
    private BannedConfig banned;

    /**
     * 重定向策略配置
     */
    private RedirectConfig redirect;

    /**
     * 自定义返回配置
     */
    private CustomReturnConfig customReturn;

    /**
     * 安全接口白名单地址
     */
    private Set<String> whitelistAddress = Collections.setOf();

    /**
     * 异常检测配置（通用异常行为检测）
     */
    private AnomalyDetectionConfig anomalyDetection = new AnomalyDetectionConfig();

    /**
     * 默认构造函数
     */
    public SecurityFilterConfig() {
    }

    /**
     * 获取安全过滤器的时间间隔毫秒数
     * @return 返回毫秒数
     */
    @JsonIgnore
    public long getSecurityTimeInterval() {
        return securityTimeIntervalUnit.toMillis(securityTimeIntervalValue);
    }

    /**
     * 获取封禁策略配置
     * @return 返回封禁策略配置
     */
    public BannedConfig getBanned() {
        this.banned = Optional.ofNullable(banned).orElse(new BannedConfig());
        return this.banned;
    }

    /**
     * 获取重定向策略配置
     * @return 返回重定向策略配置
     */
    public RedirectConfig getRedirect() {
        this.redirect = Optional.ofNullable(redirect).orElse(new RedirectConfig());
        return this.redirect;
    }

    /**
     * 获取自定义返回配置
     * @return 返回自定义返回配置
     */
    public CustomReturnConfig getCustomReturn() {
        this.customReturn = Optional.ofNullable(customReturn).orElse(new CustomReturnConfig());
        return this.customReturn;
    }

}
