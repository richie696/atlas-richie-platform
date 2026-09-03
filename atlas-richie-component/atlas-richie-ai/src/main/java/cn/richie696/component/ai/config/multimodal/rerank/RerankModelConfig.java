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
package cn.richie696.component.ai.config.multimodal.rerank;

import lombok.Data;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
 *   <li>可选 AK/SK 型适配器：{@link #accessKey} + {@link #secretKey}</li>
 * </ul>
 *
 * @author richie696
 */
@Data
public class RerankModelConfig {

    /**
     * 厂商(枚举)— 见 {@link RerankProvider}。
     */
    private RerankProvider provider;

    /**
     * 业务可读标识(默认与 Map key 相同)。
     */
    private String name;

    /**
     * Bearer 型鉴权使用的 API Key。
     */
    private String apiKey;

    /**
     * API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。
     */
    private Set<String> apiKeys = new LinkedHashSet<>();

    /**
     * AppCode 型鉴权使用的应用 Code。
     */
    private String appCode;

    /**
     * 厂商端点 URL(为空时适配器回落到默认 URL)。
     */
    private String baseUrl;

    /** 能力专属适配器编码；为空时由 provider 兼容性回退。 */
    private String adapterCode;

    /** 适配器实际请求端点；为空时使用 baseUrl。 */
    private String endpoint;

    /** 适配器认证类型提示；不承载密钥。 */
    private String authType;

    /** 模型请求参数预设，不包含密钥。 */
    private Map<String, Object> requestParameters;

    /**
     * 重排序模型名(例如 "gte-rerank" / "rerank" / "pangu-rerank"),为空时使用默认值。
     */
    private String model;

    /** 可选访问密钥；仅由声明支持 AK/SK 的适配器使用。 */
    private String accessKey;

    /** 可选安全密钥；仅由声明支持 AK/SK 的适配器使用。 */
    private String secretKey;

    /** 可选服务区域；由具体适配器解释。 */
    private String region;

    /** Viking AI Search 应用 ID；仅由 Viking AI Search 适配器使用。 */
    private String applicationId;

    /** Viking AI Search 场景/策略 ID；仅由 Viking AI Search 适配器使用。 */
    private String sceneId;

    /** Viking AI Search 请求用户 ID；可由请求级 userId 覆盖。 */
    private String userId;
}
