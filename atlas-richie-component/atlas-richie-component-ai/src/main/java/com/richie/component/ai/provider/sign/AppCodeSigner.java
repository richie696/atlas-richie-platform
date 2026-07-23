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
package com.richie.component.ai.provider.sign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AppCode 鉴权头部工具（华为云 API 网关 / 华为云盘古大模型市场版模式）。
 * <p>
 * 协议：客户端在 HTTP 头中携带 {@code X-Apig-AppCode: <appCode>}，网关层校验后转发到上游。
 * 不参与任何 HMAC / SHA 运算，仅做头部组装；故工具为极简的 final 静态工具类。
 * <p>
 * 注意：阿里云 OCR 市场版的 {@code Authorization: APPCODE <appCode>} 与本工具
 * 头部名不同（OCR 用 {@code Authorization}，华为云用 {@code X-Apig-AppCode}），
 * 因此两个 vendor 不能共用本工具 —— 阿里云鉴权见各 vendor 自带实现。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-20
 */
public final class AppCodeSigner {

    /** 华为云 API 网关 AppCode 头部名称（盘古 / SIS / ModelArts 市场版统一约定）。 */
    public static final String HEADER_APP_CODE = "X-Apig-AppCode";

    private AppCodeSigner() {
        // static utility
    }

    /**
     * 返回携带 AppCode 鉴权信息的请求头映射。
     * <p>
     * 返回 {@link LinkedHashMap} 保持单条插入顺序，便于上游 HTTP 库调试日志中按固定顺序输出。
     *
     * @param appCode 华为云 API 网关分配的应用 Code；{@code null} / 空串时返回空 Map
     * @return 包含 {@code X-Apig-AppCode} 条目的不可变快照 Map
     */
    public static Map<String, String> appCodeHeaders(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>(2);
        headers.put(HEADER_APP_CODE, appCode);
        return Collections.unmodifiableMap(headers);
    }
}
