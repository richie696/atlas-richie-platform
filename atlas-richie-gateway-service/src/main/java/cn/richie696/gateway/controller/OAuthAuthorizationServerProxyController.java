/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.gateway.controller;

import cn.richie696.gateway.config.GatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 迁移期 OAuth Endpoint 反向代理。
 *
 * <p>Gateway 不再实现 token endpoint；配置 Authorization Server 地址后，
 * 旧路径只负责转发标准 OAuth 表单请求和响应，便于客户端平滑迁移。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth2")
@ConditionalOnProperty(prefix = "platform.gateway.interface-auth", name = "authorization-server-base-uri")
public class OAuthAuthorizationServerProxyController {

    private final WebClient webClient;
    private final GatewayConfig gatewayConfig;

    public OAuthAuthorizationServerProxyController(WebClient.Builder webClientBuilder,
                                                   GatewayConfig gatewayConfig) {
        this.webClient = webClientBuilder.build();
        this.gatewayConfig = gatewayConfig;
    }

    @PostMapping(value = {"/token", "/introspect", "/revoke"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> proxy(ServerWebExchange exchange) {
        String operation = exchange.getRequest().getURI().getPath()
                .substring(exchange.getRequest().getURI().getPath().lastIndexOf('/') + 1);
        String baseUri = gatewayConfig.getOauth2().getAuthorizationServerBaseUri();
        String target = baseUri.replaceAll("/+$", "") + "/oauth2/" + operation;

        return exchange.getFormData()
                .flatMap(form -> webClient.post()
                        .uri(target)
                        .headers(headers -> copyAuthorization(exchange.getRequest().getHeaders(), headers))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(form))
                        .exchangeToMono(response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    HttpHeaders headers = new HttpHeaders();
                                    MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_JSON);
                                    headers.setContentType(contentType);
                                    return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
                                })))
                .onErrorResume(error -> {
                    log.warn("OAuth Authorization Server 代理调用失败: target={}", target, error);
                    return Mono.just(ResponseEntity.status(503)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"temporarily_unavailable\",\"error_description\":\"Authorization Server 不可用\"}"));
                });
    }

    private void copyAuthorization(HttpHeaders source, HttpHeaders target) {
        String authorization = source.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            target.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}
