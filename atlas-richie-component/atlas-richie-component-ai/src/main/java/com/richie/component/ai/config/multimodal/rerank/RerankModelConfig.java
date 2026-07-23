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
package com.richie.component.ai.config.multimodal.rerank;

import lombok.Data;

/**
 * 重排序(Rerank)模型配置 — 映射 {@code platform.component.ai.rerank.<key>}。
 *
 * <p>与 Chat 端 {@code AiChatModel} 解耦:重排不需要模型枚举 / ChatOptions,
 * 字段集合精简为 vendor / 鉴权字段 / baseUrl / model。
 *
 * <h2>鉴权字段按厂商语义归类</h2>
 * <ul>
 *   <li>Bearer 型(DashScope / 智谱):{@link #apiKey}</li>
 *   <li>AppCode 型(华为云盘古):{@link #appCode}</li>
 *   <li>AK/SK HMAC-SHA256(火山引擎 VikingDB):{@link #accessKey} + {@link #secretKey}</li>
 * </ul>
 *
 * @author richie696
 */
@Data
public class RerankModelConfig {

    /** 厂商(枚举)— 见 {@link RerankProvider}。 */
    private RerankProvider provider;

    /** 业务可读标识(默认与 Map key 相同)。 */
    private String name;

    /** Bearer 型鉴权使用的 API Key。 */
    private String apiKey;

    /** API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。 */
    private java.util.Set<String> apiKeys = new java.util.LinkedHashSet<>();

    /** AppCode 型鉴权使用的应用 Code。 */
    private String appCode;

    /** 厂商端点 URL(为空时适配器回落到默认 URL)。 */
    private String baseUrl;

    /** 重排序模型名(例如 "gte-rerank" / "rerank" / "pangu-rerank"),为空时使用默认值。 */
    private String model;

    /** 火山引擎(VikingDB)访问密钥,用于 HMAC-SHA256 签名。 */
    private String accessKey;

    /** 火山引擎(VikingDB)安全密钥。 */
    private String secretKey;

    /** 火山引擎服务区域(例如 {@code cn-north-1}),用于 HMAC-SHA256 签名 Credential Scope。 */
    private String region;
}