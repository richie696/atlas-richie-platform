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
package com.richie.component.ai.support.keypool;

import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 {@link ApiKeyValidator} — 基于 HTTP 状态码 + 错误消息关键词的通用检测器。
 *
 * <p>覆盖各家厂商共有的限流语义:
 * <ul>
 *   <li>HTTP 429 Too Many Requests(OpenAI / Anthropic / DashScope 标准)</li>
 *   <li>HTTP 403 Forbidden(key 临时被拒,例如智谱限流)</li>
 *   <li>HTTP 503(暂时性服务不可用,可能伴随限流)</li>
 *   <li>错误消息含 "rate limit" / "quota" / "too many" / "exceeded" / "throttled" 等</li>
 * </ul>
 *
 * <p>不 invalidate 的情况:
 * <ul>
 *   <li>HTTP 401 Unauthorized — 业务侧配置错误(全 key 都 401),应该 fail-fast 不应轮询</li>
 *   <li>HTTP 400 / 404 / 422 — 请求参数错误,换 key 无效</li>
 *   <li>网络异常(IOException / ConnectException) — 换 key 可能有用,但默认不 invalidate
 *       (避免误判,可由业务侧继承覆盖)</li>
 * </ul>
 *
 * @author richie696
 */
@Component
public class DefaultApiKeyValidator implements ApiKeyValidator {

    /** 需要 invalidate 的 HTTP 状态码集合。 */
    private static final Set<Integer> INVALIDATING_STATUS_CODES = Set.of(429, 403, 503);

    /** 错误消息中的限流关键词(不区分大小写)。 */
    private static final Pattern RATE_LIMIT_PATTERN = Pattern.compile(
            "\\b(rate[_\\s-]?limit|quota[_\\s-]?(exceeded)?|too[_\\s-]?many[_\\s-]?requests?|" +
                    "throttl(ed|ing)|exceed(ed|ing)[_\\s-]?(limit|quota)|" +
                    "request[_\\s-]?rate[_\\s-]?limit|frequency[_\\s-]?limit)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isKeyInvalidating(Throwable error) {
        if (error == null) {
            return false;
        }
        // 1. 优先按 HTTP 状态码判断
        Integer status = extractHttpStatus(error);
        if (status != null && INVALIDATING_STATUS_CODES.contains(status)) {
            return true;
        }

        // 2. 按错误消息关键词判断
        String message = extractMessage(error);
        if (message != null) {
            Matcher m = RATE_LIMIT_PATTERN.matcher(message);
            if (m.find()) {
                return true;
            }
        }

        return false;
    }

    /** 从异常链中提取 HTTP 状态码(支持多层包装)。 */
    private Integer extractHttpStatus(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpResponseAware) {
                int status = ((HttpResponseAware) current).getHttpStatus();
                if (status > 0) {
                    return status;
                }
            }
            // 反射常见字段 (避免硬依赖)
            Integer status = reflectStatusField(current);
            if (status != null) {
                return status;
            }
            current = current.getCause();
        }
        return null;
    }

    /** 通过反射尝试读取 {@code statusCode} / {@code status} 字段。 */
    private Integer reflectStatusField(Throwable t) {
        for (String fieldName : new String[]{"statusCode", "status", "code"}) {
            try {
                java.lang.reflect.Field f = findField(t.getClass(), fieldName);
                if (f != null) {
                    Object v = f.get(t);
                    if (v instanceof Number) {
                        int s = ((Number) v).intValue();
                        if (s >= 100 && s < 600) {
                            return s;
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private String extractMessage(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 标记接口 — 厂商 HTTP 异常可实现此接口以暴露 status code,
     * 避免本 validator 依赖具体 HTTP 客户端 SDK 的类型。
     */
    public interface HttpResponseAware {
        int getHttpStatus();
    }
}