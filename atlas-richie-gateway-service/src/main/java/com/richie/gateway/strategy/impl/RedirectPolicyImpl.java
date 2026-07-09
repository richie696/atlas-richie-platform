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
package com.richie.gateway.strategy.impl;

import com.richie.gateway.bean.RequestMetric;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.RedirectConfig;
import com.richie.gateway.strategy.SecurityPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 重定向页面策略实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:09:52
 */
@Component("redirectPolicy")
public final class RedirectPolicyImpl implements SecurityPolicy {

    @Override
    public Mono<Void> handleFailure(ServerHttpRequest request,
                                    ServerHttpResponse response, GatewayConfig config,
                                    RequestMetric requestMetric) {
        RedirectConfig redirect = config.getSecurity().getRedirect();
        response.setStatusCode(HttpStatus.SEE_OTHER);
        response.getHeaders().set(HttpHeaders.LOCATION, redirect.getSecurityRedirectUri());
        return response.setComplete();
    }

}
