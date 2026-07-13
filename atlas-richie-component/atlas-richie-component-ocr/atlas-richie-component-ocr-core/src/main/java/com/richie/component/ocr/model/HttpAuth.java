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
package com.richie.component.ocr.model;

import java.util.Objects;

/**
 * HTTP 认证信息 —— 用于 {@link OcrImage.Url} 携带下载凭证。
 *
 * @param type  认证类型（BASIC / BEARER / CUSTOM）
 * @param value 认证值（如 token / base64 credentials）
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record HttpAuth(Type type, String value) {

    /**
     * 紧凑构造器 —— 验证必传字段.
     *
     * @param type  认证类型（必传）
     * @param value 认证值（必传, 含义取决于 {@code type}）
     */
    public HttpAuth {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    /**
     * HTTP 认证类型。
     */
    public enum Type {
        /** HTTP Basic 认证 —— {@code Authorization: Basic <base64(user:pass)>} */
        BASIC,
        /** Bearer Token 认证 —— {@code Authorization: Bearer <token>} */
        BEARER,
        /** 自定义凭证 —— 由 Provider 自行解析 value（如签名 URL / 私有 token） */
        CUSTOM
    }

    /**
     * 创建 Bearer Token 认证（最常见的私有 OSS 临时 URL 场景）。
     *
     * @param token 令牌原文
     * @return Bearer 认证实例
     */
    public static HttpAuth bearer(String token) {
        return new HttpAuth(Type.BEARER, token);
    }

    /**
     * 创建 HTTP Basic 认证。
     *
     * @param credentials Base64 编码后的 {@code user:pass}（业务侧自行编码）
     * @return Basic 认证实例
     */
    public static HttpAuth basic(String credentials) {
        return new HttpAuth(Type.BASIC, credentials);
    }
}
