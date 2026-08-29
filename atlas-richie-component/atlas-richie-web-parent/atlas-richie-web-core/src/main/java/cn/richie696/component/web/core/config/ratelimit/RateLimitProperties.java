/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.web.core.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 业务限流配置契约；Redis 限流实现位于可选 atlas-richie-web-rate-limiter 模块。 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.rate-limit")
public class RateLimitProperties {
    private boolean enabled = false;
    private String keyPrefix = "richie:web:rate-limit";
    private int permitsPerSecond = 50;
    private int windowSeconds = 1;
    private int denyStatus = 429;
    private String denyCode = "RATE_LIMITED";
    private String denyMsg = "请求过于频繁，请稍后再试 (key={key})";
    private Map<String, String> denyHeaders = new HashMap<>();
    private boolean requireGatewayIdentity = true;
    private String gatewayIdentityHeader = "X-Forwarded-From-Gateway";
    private int maxClientKeyLength = 256;
    private List<RouteConfig> routes = new ArrayList<>();

    @Data
    public static class RouteConfig {
        private String pattern;
        private int permitsPerSecond = 50;
        private Integer windowSeconds;
        private Integer denyStatus;
        private String denyCode;
        private String denyMsg;
        private Map<String, String> denyHeaders;
    }
}
