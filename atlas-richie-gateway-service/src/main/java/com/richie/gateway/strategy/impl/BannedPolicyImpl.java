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
package com.richie.gateway.strategy.impl;

import com.richie.gateway.bean.RequestMetric;
import com.richie.gateway.config.BannedConfig;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.enums.SecurityRuleEnum;
import com.richie.gateway.strategy.SecurityPolicy;
import com.richie.gateway.utils.NetworkUtils;
import com.richie.contract.exception.PlatformRuntimeException;
import com.richie.component.cache.GlobalCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 * 封禁IP策略实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:09:13
 */
@Component("bannedPolicy")
public final class BannedPolicyImpl implements SecurityPolicy {

    @Override
    public Mono<Void> handleFailure(ServerHttpRequest request, ServerHttpResponse response, GatewayConfig config, RequestMetric requestMetric) {
        requestMetric.setRule(SecurityRuleEnum.BANNED_IP);
        String ip = NetworkUtils.getIP(request);
        BannedConfig banned = config.getSecurity().getBanned();
        if (banned.getPermanent()) {
            GlobalCache.collection().add(banned.getPermanentPath(), ip);
            return NetworkUtils.returnError(response, HttpStatus.BAD_REQUEST, "禁止访问");
        }
        long securityBlockTimeMillis = banned.getSecurityBlockTimeMillis();
        Date blockDate = new Date(System.currentTimeMillis() + securityBlockTimeMillis);
        requestMetric.setBlockTime(blockDate);
        GlobalCache.struct().set(config.getVisitRecordPath() + ip, requestMetric, securityBlockTimeMillis);
        return NetworkUtils.returnError(response, HttpStatus.BAD_REQUEST, "禁止访问，您在" + banned.getSecurityBlockTime() + getTimeText(banned.getSecurityBlockTimeUnit()) + "后可以继续访问。");
    }

    private String getTimeText(@Nonnull TimeUnit unit) {
        return switch (unit) {
            case DAYS -> "天";
            case HOURS -> "小时";
            case MINUTES -> "分钟";
            case SECONDS -> "秒";
            case MILLISECONDS -> "毫秒";
            default -> throw new PlatformRuntimeException("错误的时间单位");
        };
    }
}
