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

import reactor.core.publisher.Mono;

/**
 * 租户过期/废弃通知端口。
 *
 * <p>租户拦截器只依赖这个最小接口，不感知 Feign、RestClient、gRPC、Redis
 * 或其它具体通信方式。接入方可按项目实际通信能力提供实现。</p>
 */
@FunctionalInterface
public interface TenantExpiredNotifier {

    /**
     * 通知业务系统作废租户账号。
     *
     * @param tenantId 租户 ID
     * @return 通知已被当前实现接受返回 {@code true}；通知失败返回 {@code false}
     */
    Mono<Boolean> notifyExpired(String tenantId);
}
