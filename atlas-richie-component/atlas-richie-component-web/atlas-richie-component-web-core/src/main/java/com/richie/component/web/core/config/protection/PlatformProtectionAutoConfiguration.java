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
package com.richie.component.web.core.config.protection;

import com.richie.component.web.core.protection.AnomalyDetectionInterceptor;
import com.richie.component.web.core.protection.BruteForceInterceptor;
import com.richie.component.web.core.protection.LongLivedPathBypass;
import com.richie.component.web.core.protection.LoginAttemptTracker;
import com.richie.component.web.core.protection.PlatformProtectionInterceptor;
import com.richie.component.web.core.protection.RequestSizeGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 平台防护层装配（README.md §4.8）。
 * <p>
 * 本类<strong>只</strong>负责把 {@code com.richie.component.web.core.protection} 包的拦截器串成
 * 一组 {@code @Bean}——是否生效<strong>完全由配置驱动</strong>：
 * <ul>
 *   <li>{@code platform.component.web.protection.enabled=false} → 所有 bean 跳过</li>
 *   <li>{@code platform.component.web.protection.request-size.enabled=false} → 仅 RequestSizeGuard / PlatformProtectionInterceptor 不创建 RequestSize 部分</li>
 *   <li>各子项独立可关（{@code anomaly-detection} / {@code brute-force}）</li>
 * </ul>
 * <p>所有 bean 后续由 {@code WebMvcInterceptorsAutoConfiguration} 自动收拢进
 * {@code InterceptingFilter} 串——本类无需关心 MVC 集成。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(PlatformProtectionInterceptor.class)
@ConditionalOnProperty(prefix = "platform.component.web.protection", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PlatformProtectionAutoConfiguration {

    /**
     * RequestSizeGuard：请求体 / header 字节超限拦截。子开关
     * {@code platform.component.web.protection.request-size.enabled} 默认 true。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.protection.request-size", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RequestSizeGuard requestSizeGuard(PlatformProtectionProperties properties) {
        var c = properties.getRequestSize();
        log.info("PlatformProtectionAutoConfiguration: RequestSizeGuard maxBody={}B maxHeader={}B",
                c.getMaxBodyBytes(), c.getMaxHeaderBytes());
        return new RequestSizeGuard(c.getMaxBodyBytes(), c.getMaxHeaderBytes(),
                c.getBodyDenyStatus(), c.getHeaderDenyStatus());
    }

    /**
     * LongLivedPathBypass：长连接路径旁路（命中后 HangDetection 跳过 watchdog）。
     */
    @Bean
    @ConditionalOnMissingBean
    public LongLivedPathBypass longLivedPathBypass(PlatformProtectionProperties properties) {
        List<String> patterns = properties.getLongLived().getPathPatterns();
        log.info("PlatformProtectionAutoConfiguration: LongLivedPathBypass patterns={}", patterns);
        return new LongLivedPathBypass(patterns);
    }

    /**
     * PlatformProtectionInterceptor：Gateway 互斥 + RequestSize + LongLived 入口拦截。
     * <p>总是创建（受总开关 {@code protection.enabled} 控制）；其 RequestSize 子能力依赖
     * {@link #requestSizeGuard} bean 是否存在——未启用时 guard=null，RequestSize 子检查跳过。
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformProtectionInterceptor platformProtectionInterceptor(
            org.springframework.beans.factory.ObjectProvider<RequestSizeGuard> requestSizeGuardProvider,
            LongLivedPathBypass longLivedPathBypass,
            PlatformProtectionProperties properties) {
        RequestSizeGuard guard = requestSizeGuardProvider.getIfAvailable();
        if (guard == null) {
            log.info("PlatformProtectionAutoConfiguration: PlatformProtectionInterceptor (RequestSize 子能力已禁用)");
        } else {
            log.info("PlatformProtectionAutoConfiguration: PlatformProtectionInterceptor (含 RequestSize 子能力)");
        }
        return new PlatformProtectionInterceptor(guard, longLivedPathBypass, properties.getRequestSize());
    }

    /**
     * AnomalyDetectionInterceptor：Bot UA / IP 黑名单拦截。子开关
     * {@code platform.component.web.protection.anomaly-detection.enabled} 默认 true。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.protection.anomaly-detection", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AnomalyDetectionInterceptor anomalyDetectionInterceptor(PlatformProtectionProperties properties) {
        var c = properties.getAnomalyDetection();
        log.info("PlatformProtectionAutoConfiguration: AnomalyDetectionInterceptor enabled botPatterns={} ipBlacklist={}",
                c.getBotUserAgents(), c.getIpBlacklist());
        return new AnomalyDetectionInterceptor(
                c.getBotUserAgents(), c.getIpBlacklist(),
                c.getDenyStatus(), c.getDenyCode(), c.getDenyMsg());
    }

    /**
     * LoginAttemptTracker：登录失败计数器（被 BruteForceInterceptor 使用）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.protection.brute-force", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public LoginAttemptTracker loginAttemptTracker(PlatformProtectionProperties properties) {
        var c = properties.getBruteForce();
        log.info("PlatformProtectionAutoConfiguration: LoginAttemptTracker window={}s maxAttempts={} lockout={}s",
                c.getWindowSeconds(), c.getMaxAttempts(), c.getLockoutSeconds());
        return new LoginAttemptTracker(c.getWindowSeconds(), c.getMaxAttempts(), c.getLockoutSeconds());
    }

    /**
     * BruteForceInterceptor：登录防爆破。子开关
     * {@code platform.component.web.protection.brute-force.enabled} 默认 true。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.protection.brute-force", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public BruteForceInterceptor bruteForceInterceptor(
            LoginAttemptTracker tracker, PlatformProtectionProperties properties) {
        var c = properties.getBruteForce();
        log.info("PlatformProtectionAutoConfiguration: BruteForceInterceptor denyStatus={} code={}",
                c.getDenyStatus(), c.getDenyCode());
        return new BruteForceInterceptor(tracker, c.getDenyStatus(), c.getDenyCode(), c.getDenyMsg(),
                c.getLockoutSeconds());
    }
}