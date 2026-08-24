/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.config;

import cn.richie696.contract.gateway.config.GatewayContract;
import cn.richie696.gateway.fallback.GlobalFallbackController;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway 最小真实 HTTP 链路：启动 WebFlux 服务器、走真实 HTTP 请求，并验证同一上下文中的
 * Gateway Secret 已由 Vault Provider 注入。真实下游路由转发不在本用例中伪造，另行由路由 E2E 覆盖。
 */
@Tag("integration")
@EnabledIf("cn.richie696.gateway.config.GatewayVaultHttpIntegrationTest#isEnabled")
@SpringBootTest(
        classes = GatewayVaultHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=orders",
                "platform.component.secret.enabled=true",
                "platform.component.secret.strict-mode=true",
                "platform.component.secret.property-source.application=orders",
                "platform.component.secret.property-source.environment=e2e",
                "platform.component.secret.refresh.enabled=false",
                "platform.component.secret.vault.endpoint=${ATLAS_SECRET_VAULT_ENDPOINT:http://127.0.0.1:8200}",
                "platform.component.secret.vault.authentication.type=token",
                "platform.component.secret.vault.authentication.token=${ATLAS_SECRET_VAULT_TOKEN:}",
                "platform.component.secret.vault.kv.mount=kv",
                "platform.component.secret.vault.transit.mount=transit"
        })
class GatewayVaultHttpIntegrationTest {

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    AuthenticationConfig authenticationConfig;

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && token() != null;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> "0");
        registry.add("spring.application.name", () -> "orders");
        registry.add("spring.main.web-application-type", () -> "reactive");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "16379");
        registry.add("spring.data.redis.password", () -> "Redis2025!Local");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
        registry.add("platform.cache.redis.stream.enabled", () -> "false");
        registry.add("platform.cache.redis.stream.monitoring.enabled", () -> "false");
        registry.add("platform.cache.redis.stream.tracing.enabled", () -> "false");
        registry.add("platform.cache.redis.stream.monitor.otlp.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () -> String.join(",",
                "cn.richie696.component.cache.config.CacheAutoConfiguration",
                "cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration",
                "cn.richie696.component.cache.local.config.LocalCacheAutoConfiguration"));
        registry.add("platform.component.secret.enabled", () -> "true");
        registry.add("platform.component.secret.strict-mode", () -> "true");
        registry.add("platform.component.secret.property-source.application", () -> "orders");
        registry.add("platform.component.secret.property-source.environment", () -> "e2e");
        registry.add("platform.component.secret.refresh.enabled", () -> "false");
        registry.add("platform.component.secret.vault.endpoint", GatewayVaultHttpIntegrationTest::endpoint);
        registry.add("platform.component.secret.vault.authentication.type", () -> "token");
        registry.add("platform.component.secret.vault.authentication.token", GatewayVaultHttpIntegrationTest::token);
        registry.add("platform.component.secret.vault.kv.mount", () -> "kv");
        registry.add("platform.component.secret.vault.transit.mount", () -> "transit");
    }

    @Test
    void realHttpRequestUsesGatewayControllerAndVaultSecretIsBound() {
        WebClient client = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        Map<?, ?> response = client.get()
                .uri("/fallback/default")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        assertThat(response).isNotNull();
        assertThat(authenticationConfig.getSecretKey()).isEqualTo("gateway-from-vault");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "cn.richie696.component.cache.config.CacheAutoConfiguration",
            "cn.richie696.component.cache.redis.config.base.RedisBaseAutoConfiguration",
            "cn.richie696.component.cache.local.config.LocalCacheAutoConfiguration"
    })
    @Import({GatewayConfig.class, GatewayContract.class, AuthenticationConfig.class, GlobalFallbackController.class})
    static class TestApplication {
    }

    private static String endpoint() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_ENDPOINT"},
                new String[]{"atlas.secret.vault.endpoint"},
                "http://127.0.0.1:8200");
    }

    private static String token() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_TOKEN"},
                new String[]{"atlas.secret.vault.token"});
    }
}
