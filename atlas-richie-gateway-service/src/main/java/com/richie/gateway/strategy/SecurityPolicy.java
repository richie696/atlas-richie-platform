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
package com.richie.gateway.strategy;

import com.richie.gateway.bean.RequestMetric;
import com.richie.gateway.config.GatewayConfig;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * 安全策略接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:42:17
 */
public interface SecurityPolicy {

    /**
     * 处理失败
     *
     * @param request 请求对象
     * @param response 应答对象
     * @param config 安全过滤器配置
     * @param requestMetric 请求指标
     * @return 返回处理结果
     */
    Mono<Void> handleFailure(ServerHttpRequest request, ServerHttpResponse response, GatewayConfig config, RequestMetric requestMetric);

}
