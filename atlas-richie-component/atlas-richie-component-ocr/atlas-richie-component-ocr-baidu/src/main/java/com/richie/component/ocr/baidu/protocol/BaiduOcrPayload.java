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
package com.richie.component.ocr.baidu.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ocr.model.OcrOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 百度智能云 OCR {@code /rest/2.0/ocr/v1/general_basic} 端点请求 wire-format record。
 *
 * <p>把原 {@code StringBuilder form = new StringBuilder(); form.append("...").append(URLEncoder.encode(...))}
 * 手动拼接链改为 typed record + {@link #toFormBody()} 一行 form-encoding。
 *
 * <p>字段语义（百度协议文档
 * <a href="https://ai.baidu.com/ai-doc/OCR/rkibizxtw" target="_blank">通用文字识别</a>）:
 * <ul>
 *   <li>{@code access_token}: OAuth2 access_token，每次请求必填</li>
 *   <li>{@code url} / {@code image} 二选一 —— URL 模式或裸 base64 模式</li>
 *   <li>{@code language_type}: 百度原生语言代码（{@code CHN_ENG} / {@code ENG} / {@code JAP} 等），必填</li>
 *   <li>{@code detect_direction}: 是否检测图像朝向（{@code true}/{@code false}）</li>
 *   <li>{@code recognize_granularity}: 是否返回单字结果；{@code OcrOptions.tableRecognition()} 为 {@code true}
 *       时固定传 {@code "big"}（百度表格识别模式），其它情况下字段省略</li>
 *   <li>{@code prob}: 是否返回识别概率（{@code true}/{@code false}）</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} 字段级策略: {@code String} 字段的 {@code null} 自动从输出中剔除
 * （用于未来若改用 JSON 序列化的兼容性；本类型当前以 form-urlencoded 上行）。
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} 允许反序列化忽略百度协议未来新增字段。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaiduOcrPayload(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("url") String url,
        @JsonProperty("image") String image,
        @JsonProperty("language_type") String languageType,
        @JsonProperty("detect_direction") boolean detectDirection,
        @JsonProperty("recognize_granularity") String recognizeGranularity,
        @JsonProperty("prob") boolean prob) {

    /**
     * 构造 base64 image payload: {@code url=null}, {@code image=base64}。
     *
     * @param accessToken OAuth2 access_token
     * @param base64 图片字节 Base64（不含 {@code data:} URI 前缀）；不能为 {@code null}
     * @param languageType 百度原生语言代码；不能为 {@code null}
     * @param options 调用侧 OCR 选项，用于驱动 {@code detect_direction} / {@code recognize_granularity} / {@code prob}
     * @return 新的 BaiduOcrPayload 实例
     */
    public static BaiduOcrPayload ofBase64(String accessToken, String base64, String languageType, OcrOptions options) {
        return new BaiduOcrPayload(
                accessToken,
                null,
                base64,
                languageType,
                options.detectOrientation(),
                options.tableRecognition() ? "big" : null,
                options.outputBoundingBoxes());
    }

    /**
     * 构造 URL image payload: {@code image=null}, {@code url=imageUrl}。
     *
     * @param accessToken OAuth2 access_token
     * @param imageUrl 图片公网访问地址；不能为 {@code null}
     * @param languageType 百度原生语言代码；不能为 {@code null}
     * @param options 调用侧 OCR 选项，用于驱动 {@code detect_direction} / {@code recognize_granularity} / {@code prob}
     * @return 新的 BaiduOcrPayload 实例
     */
    public static BaiduOcrPayload ofUrl(String accessToken, String imageUrl, String languageType, OcrOptions options) {
        return new BaiduOcrPayload(
                accessToken,
                imageUrl,
                null,
                languageType,
                options.detectOrientation(),
                options.tableRecognition() ? "big" : null,
                options.outputBoundingBoxes());
    }

    /**
     * 把当前 payload 序列化为 {@code application/x-www-form-urlencoded} body。
     *
     * <p>输出键值对顺序与百度协议文档保持一致:
     * {@code access_token} → ( {@code url} | {@code image} ) → {@code language_type}
     * → {@code detect_direction} → ( {@code recognize_granularity} ) → {@code prob}。
     *
     * <p>{@code recognize_granularity} 在 {@code null} 时整段 {@code key=value} 对都省略（与之前
     * {@code if (request.options().tableRecognition()) form.append("&recognize_granularity=big");} 等价）。
     *
     * @return 已 URL-encoded 的 form body 字符串
     */
    public String toFormBody() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("access_token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        if (url != null) {
            sb.append("&url=").append(URLEncoder.encode(url, StandardCharsets.UTF_8));
        } else {
            sb.append("&image=").append(URLEncoder.encode(image, StandardCharsets.UTF_8));
        }
        sb.append("&language_type=").append(URLEncoder.encode(languageType, StandardCharsets.UTF_8));
        sb.append("&detect_direction=").append(detectDirection);
        if (recognizeGranularity != null) {
            sb.append("&recognize_granularity=").append(URLEncoder.encode(recognizeGranularity, StandardCharsets.UTF_8));
        }
        sb.append("&prob=").append(prob);
        return sb.toString();
    }
}
