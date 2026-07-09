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
package com.richie.context.utils.web;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * Servlet 相关工具类。
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    /**
     * 获取客户端真实 IP，支持代理/负载均衡场景。
     * <p>
     * 按以下优先级依次尝试：
     * <ol>
     *     <li>X-Forwarded-For（取第一个非 unknown 的 IP）</li>
     *     <li>X-Real-IP</li>
     *     <li>Proxy-Client-IP</li>
     *     <li>WL-Proxy-Client-IP</li>
     *     <li>request.getRemoteAddr()（兜底）</li>
     * </ol>
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP，不会返回 {@code null}
     */
    public static String getClientIP(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip.trim())) {
                int commaIdx = ip.indexOf(',');
                return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
            }
        }

        return request.getRemoteAddr();
    }

}
