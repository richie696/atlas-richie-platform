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
package com.richie.component.ai.config.multimodal.audio;

import lombok.Data;

/**
 * 音频模型(TTS / STT / VoiceChat)配置基类 — 承载三域共用的鉴权 / 端点 / 模型名字段。
 *
 * <p>{@code vendor} 字段不在基类,放在各子类以强制类型安全
 * (不同能力的厂商枚举不同 — TTS 不能配 STT 私有的 vendor)。
 *
 * <h2>字段按鉴权模式归类</h2>
 * <ul>
 *   <li>Bearer 型(智谱 / 豆包):{@link #apiKey}</li>
 *   <li>TC3 型(腾讯云混元):{@link #secretId} + {@link #secretKey} + {@link #region} + {@link #endpoint}</li>
 *   <li>AppCode 型(华为云 SIS / 盘古):{@link #appCode}</li>
 *   <li>AK/SK HMAC 型(火山 VikingDB):{@link #apiKey} + {@link #secretKey}</li>
 * </ul>
 *
 * @author richie696
 */
@Data
public abstract class AbstractAudioModelConfig {

    /** 业务可读标识(默认与 Map key 相同)。 */
    private String name;

    /** Bearer 鉴权使用的 API Key(智谱 / 豆包 openspeech 等)。 */
    private String apiKey;

    /** API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。 */
    private java.util.Set<String> apiKeys = new java.util.LinkedHashSet<>();

    /** TC3(腾讯云)鉴权使用的 SecretId。 */
    private String secretId;

    /** TC3(腾讯云) / AWS4(火山)鉴权使用的 SecretKey。 */
    private String secretKey;

    /** AppCode 鉴权使用的应用 Code(华为云 SIS / 盘古大模型市场版)。 */
    private String appCode;

    /** 厂商端点 URL(为空时各 vendor impl 自行回落默认值)。 */
    private String baseUrl;

    /** 语音模型名(如 {@code glm-tts} / {@code seed-tts-2.0} / {@code hunyuan-tts} / {@code glm-4-voice})。 */
    private String model;

    /** TC3 / AWS4 鉴权使用的区域(如 {@code ap-guangzhou} / {@code cn-north-1})。 */
    private String region;

    /** TC3 鉴权使用的服务端点主机(如 {@code tts.tencentcloudapi.com})。 */
    private String endpoint;

    /** 豆包 openspeech 资源 ID(可选)。 */
    private String resourceId;

    /** 豆包 openspeech 应用 ID(可选)。 */
    private String appId;
}