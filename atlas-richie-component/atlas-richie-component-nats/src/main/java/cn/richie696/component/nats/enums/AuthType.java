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
package cn.richie696.component.nats.enums;

/**
 * NATS 认证类型枚举
 *
 * @author richie696
 * @since 1.0.0
 */
public enum AuthType {

    /**
     * 无认证：开放连接或仅受信网络内部使用。
     */
    NONE,

    /**
     * 静态 Token 认证：使用 {@code NATS_AUTH_TOKEN}。
     */
    TOKEN,

    /**
     * 用户名/密码认证：使用 {@code NATS_USER} / {@code NATS_PASSWORD}。
     */
    USERPASS,

    /**
     * NKey 认证：基于 Ed25519 公私钥对。
     */
    NKEY,

    /**
     * 凭据文件认证：NATS 标准的 {@code .creds} 文件（含种子 + JWT）。
     */
    CREDENTIALS,

    /**
     * JWT 认证：单独传入 JWT 字符串（不含私钥），需要配合 NKey/TLS 使用。
     */
    JWT
}
