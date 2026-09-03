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
package cn.richie696.component.ai.config.multimodal.image;

import lombok.Data;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文生图(Image)模型配置 — 映射 {@code platform.component.ai.image.<key>}。
 *
 * <p>当前仅 {@code BAILIAN} 落地。
 *
 * @author richie696
 */
@Data
public class ImageModelConfig {

    /**
     * 厂商(枚举)— 见 {@link ImageProvider}。
     */
    private ImageProvider provider;

    /**
     * 业务可读标识(默认与 Map key 相同)。
     */
    private String name;

    /**
     * DashScope / 其他厂商的 API Key。
     */
    private String apiKey;

    /**
     * API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。
     */
    private Set<String> apiKeys = new LinkedHashSet<>();

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
     * 文生图模型名(例如 "wanx-v1"),为空时使用默认值。
     */
    private String model;
}
