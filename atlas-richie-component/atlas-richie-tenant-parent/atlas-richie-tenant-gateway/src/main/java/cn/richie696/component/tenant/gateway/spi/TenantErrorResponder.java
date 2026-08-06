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
package cn.richie696.component.tenant.gateway.spi;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 租户拦截器错误响应端口。
 *
 * <p>拦截器只提供错误消息键，不直接依赖国际化解析器或 Gateway 响应工具。</p>
 */
@FunctionalInterface
public interface TenantErrorResponder {

    /**
     * 返回未授权响应。
     *
     * @param exchange 当前请求交换器
     * @param messageKey 国际化消息键
     * @return 响应写入完成信号
     */
    Mono<Void> unauthorized(ServerWebExchange exchange, String messageKey);
}
