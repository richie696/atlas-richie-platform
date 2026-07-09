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
package com.richie.gateway.filter.internal.routing;

import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 网关身份透传过滤器
 * <p>
 * 强制给每个出站请求加 {@code X-Forwarded-From-Gateway: <env>:<cluster>:<instance>} header，
 * 让 web 端（{@code atlas-richie-component-web-core}）识别"已通过 gateway"，
 * 跳过 §4.8 B 组防护（AnomalyDetection / BruteForce / ApiSignature）。
 * <p>
 * gateway-id 三段以 {@code :} 分隔（避免 env 含 {@code .} 时的冲突，如 {@code dev.us-east-1}）：
 * <ul>
 *   <li><strong>env</strong>：{@code spring.profiles.active[0]}；空则回退 {@code prod}</li>
 *   <li><strong>cluster</strong>：从环境变量 {@code PLATFORM_GATEWAY_CLUSTER} 或 {@code CLUSTER_NAME} 取；空则回退 {@code default}</li>
 *   <li><strong>instance</strong>：从环境变量 {@code HOSTNAME} 取（k8s 默认注入）；空则回退 {@link InetAddress#getLocalHost()} 主机名</li>
 * </ul>
 *
 * <h2>为什么 id 在构造时算一次</h2>
 * <p>gateway-id 在 filter 实例化时计算并缓存为字段，<b>不会</b>每次请求重新计算。
 * <ul>
 *   <li>性能：避免每请求做环境变量读取与 hostname DNS 查询</li>
 *   <li>幂等：id 不会随环境变量中途变化（理论上 k8s POD 内 hostname 不变）</li>
 *   <li>可观测：日志中始终打印同一个 gateway-id，便于审计追溯</li>
 * </ul>
 *
 * <h2>执行顺序</h2>
 * <p>{@link FilterOrder#GATEWAY_IDENTITY_HEADER_FILTER}（451），位于 CanaryIdExtractor 之后，
 * CanaryLoadBalancer 之前。在 CanaryIdExtractor 之后跑，确保不被后续业务 filter 覆盖 header。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@Component
public class GatewayIdentityHeaderFilter extends AbstractBaseFilter {

    /**
     * 网关身份 header 名
     * <p>未抽出到 GlobalConstants：等 A-4 阶段 web-core 实现 gateway 互斥时一同抽出。
     */
    public static final String HEADER_NAME = "X-Forwarded-From-Gateway";

    /**
     * 网关身份 id（{@code <env>:<cluster>:<instance>}），filter 实例化时计算一次。
     */
    private final String gatewayId;

    public GatewayIdentityHeaderFilter(GatewayConfig config, I18nResolver i18n, Environment environment) {
        super(config, i18n);
        this.gatewayId = buildGatewayId(environment);
        log.info("GatewayIdentityHeaderFilter initialized with gateway-id={}", gatewayId);
    }

    /**
     * 计算 gateway-id 三段。
     */
    private static String buildGatewayId(Environment environment) {
        String env = resolveEnv(environment);
        String cluster = resolveCluster(environment);
        String instance = resolveInstance(environment);
        return env + ":" + cluster + ":" + instance;
    }

    private static String resolveEnv(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "prod";
        }
        return profiles[0];
    }

    private static String resolveCluster(Environment environment) {
        String cluster = firstNonBlank(
                environment.getProperty("PLATFORM_GATEWAY_CLUSTER"),
                environment.getProperty("CLUSTER_NAME"));
        return cluster != null ? cluster : "default";
    }

    private static String resolveInstance(Environment environment) {
        String hostname = environment.getProperty("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve hostname, falling back to 'unknown': {}", e.getMessage());
            return "unknown";
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return FilterOrder.GATEWAY_IDENTITY_HEADER_FILTER.getOrder();
    }

    /**
     * 总是启用——这是 gateway 端对 web 端的契约，必须每个请求都加 header。
     */
    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return true;
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(builder -> builder.header(HEADER_NAME, gatewayId))
                .build();
        return chain.filter(modifiedExchange);
    }
}