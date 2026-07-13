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
package com.richie.component.ocr.volcano.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 火山引擎视觉智能 OCR {@code /?Action=RecognizeImage&Version=2020-08-26} 端点请求 wire-format record。
 *
 * <p>把原 {@code ObjectNode body = JsonNodeFactory.instance.objectNode(); body.put(...)}
 * 手动构造改为 typed record + {@code JsonUtils.getInstance().serialize(this)} 一行序列化。</p>
 *
 * <p>字段语义:
 * <ul>
 *   <li>{@code image_base64} 待识别图片的 base64 字符串（不带 {@code data:} URI 前缀），由 {@link VolcanoRequest#imageBase64()} 提供</li>
 *   <li>{@code approximate_pixel} 长边像素估值；火山引擎 OCR 默认 0 表示服务端自动估算</li>
 *   <li>{@code mode} 识别模式；固定 {@code "default"} —— 火山引擎通用文字识别</li>
 *   <li>{@code filter_thresh} 低分文本过滤阈值（0-100）；固定 {@code 80}，低于该分数的行被丢弃</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} 字段级策略: 字段均为非空常量或必填 base64，没有可省略字段；该注解保留以与
 * Aliyun 同类 record 保持一致风格，并防止未来加可空字段时误序列化。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param imageBase64 待识别图片的 base64 字符串
 * @param approximatePixel 长边像素估值
 * @param mode 识别模式
 * @param filterThresh 低分文本过滤阈值
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VolcanoOcrPayload(
        @JsonProperty("image_base64") String imageBase64,
        @JsonProperty("approximate_pixel") int approximatePixel,
        @JsonProperty("mode") String mode,
        @JsonProperty("filter_thresh") int filterThresh) {

    /** 火山引擎默认 mode（通用文字识别）。 */
    private static final String DEFAULT_MODE = "default";
    /** 火山引擎默认低分文本过滤阈值。 */
    private static final int DEFAULT_FILTER_THRESH = 80;
    /** 火山引擎默认长边像素估值（0 = 服务端自动估算）。 */
    private static final int DEFAULT_APPROXIMATE_PIXEL = 0;

    /** 构造 base64 image payload（火山引擎仅支持 base64，不支持 URL 直传）。 */
    public static VolcanoOcrPayload ofBase64(String base64) {
        return new VolcanoOcrPayload(
                base64,
                DEFAULT_APPROXIMATE_PIXEL,
                DEFAULT_MODE,
                DEFAULT_FILTER_THRESH);
    }
}