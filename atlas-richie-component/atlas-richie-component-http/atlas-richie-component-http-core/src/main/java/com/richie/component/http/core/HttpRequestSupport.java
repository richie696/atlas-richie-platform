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
package com.richie.component.http.core;

import com.richie.context.utils.data.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


/**
 * 跨 Provider 共享的 HTTP 请求辅助逻辑。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public final class HttpRequestSupport {

    private HttpRequestSupport() {
    }

    /**
     * 统一构建带查询参数的 URL。
     * <p>
     * 该方法会自动处理已有 query、URL fragment（#xxx）和 UTF-8 编码。
     */
    public static String buildUrlWithParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }

        // 先拆分 fragment，避免参数被拼接到 # 后面导致请求端无法识别。
        String fragment = "";
        String base = url;
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0) {
            base = url.substring(0, fragmentIndex);
            fragment = url.substring(fragmentIndex);
        }

        // URL 已经带 query 时追加 &，否则追加 ?。
        StringBuilder sb = new StringBuilder(base);
        if (!base.contains("?")) {
            sb.append('?');
        } else if (!base.endsWith("?") && !base.endsWith("&")) {
            sb.append('&');
        }

        // 跳过 null key，避免出现非法 query 片段。
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }

        return sb.append(fragment).toString();
    }

    /**
     * 统一请求体序列化策略：
     * <ul>
     *   <li>String：UTF-8 字节</li>
     *   <li>Object：JSON 序列化</li>
     * </ul>
     */
    public static byte[] serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        return JsonUtils.getInstance().serializeBytes(body);
    }

    /**
     * 在同步调用场景为执行器附加超时控制。
     *
     * @param timeout  超时时间；为空或非法值时不启用超时
     * @param supplier 实际执行逻辑
     */
    public static <T> T executeWithTimeout(Duration timeout, Supplier<T> supplier) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return supplier.get();
        }
        try {
            return CompletableFuture.supplyAsync(supplier)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
