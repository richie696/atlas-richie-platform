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
package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

import java.util.Map;

/**
 * 平台防护层 B 组：登录暴力破解保护（README.md §4.8.2 / §5.2 A-5）。
 * <p>
 * <strong>拦截点</strong>：仅作用于 {@code POST} 登录端点。检测到 {@code X-Forwarded-From-Gateway}
 * header → 放行（gateway 端已做）。
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 210：在 AnomalyDetection(200) 之后、KeyResolver(250) 之前。
 *
 * <h2>依赖</h2>
 * <p>{@link LoginAttemptTracker} 提供 in-memory 滑动窗口；多实例部署需换 Redis。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class BruteForceInterceptor implements WebInterceptor, Ordered {

    /** 决策事件 attribute key，供 §4.5 HookBus 监听。 */
    public static final String DECISION_ATTRIBUTE = "platform.web.brute-force-decision";

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 210;

    /** 拦截路径：仅作用于登录端点 POST 调用。 */
    public static final String LOGIN_PATH = "/login";

    private final LoginAttemptTracker tracker;
    private final int denyStatus;
    private final String denyCode;
    private final String denyMsg;
    private final long lockoutSeconds;

    public BruteForceInterceptor(LoginAttemptTracker tracker,
                                 int denyStatus,
                                 String denyCode,
                                 String denyMsg,
                                 long lockoutSeconds) {
        this.tracker = tracker;
        this.denyStatus = denyStatus;
        this.denyCode = denyCode;
        this.denyMsg = denyMsg;
        this.lockoutSeconds = lockoutSeconds;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        if (isGatewayBypassed(ctx)) {
            chain.proceed(ctx);
            return;
        }
        if (!LOGIN_PATH.equals(ctx.path()) || !"POST".equalsIgnoreCase(ctx.method())) {
            chain.proceed(ctx);
            return;
        }
        String key = ctx.clientKey();
        if (key == null || key.isBlank()) {
            chain.proceed(ctx);
            return;
        }
        if (tracker.isLocked(key)) {
            String body = renderBody();
            ctx.setAttribute(DECISION_ATTRIBUTE,
                    Map.of("type", "locked", "key", key, "lockoutSeconds", lockoutSeconds));
            ctx.markShortCircuit(denyStatus, body);
            log.info("BruteForce deny (locked): key={}", key);
            return;
        }
        chain.proceed(ctx);
    }

    private String renderBody() {
        String msg = denyMsg.replace("{lockout}", String.valueOf(lockoutSeconds));
        return ApiResult.error(denyCode, msg).toJson();
    }

    private static boolean isGatewayBypassed(WebRequestContext ctx) {
        Boolean flag = ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE);
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        return ctx.header(PlatformProtectionInterceptor.GATEWAY_HEADER) != null;
    }

    /**
     * 公开给业务层：登录失败时调用以记录；登录成功时调用以清窗口。
     */
    public LoginAttemptTracker tracker() {
        return tracker;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}