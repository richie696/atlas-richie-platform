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
package com.richie.component.ocr.tencent.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 腾讯云 OCR {@code ocr.tencentcloudapi.com} 端点请求 wire-format record。
 *
 * <p>把原 {@code ObjectNode bodyJson = JsonNodeFactory.instance.objectNode(); bodyJson.put(...)}
 * 手动构造改为 typed record + {@code JsonUtils.serialize(this)} 一行序列化。
 *
 * <p>签名兼容性: TC3-HMAC-SHA256 签名由
 * {@link com.richie.component.ocr.tencent.provider.TencentOcrProvider} 计算，
 * 签名输入是 {@code JsonUtils.serialize(payload)} 之后的字符串本身（不是 record 对象），
 * 因此 record 与原 {@code ObjectNode} 只要输出 JSON 字节相同，签名结果就一致。
 *
 * <p>{@code @JsonInclude(NON_NULL)} 字段级策略: 任何 {@code null} 字段自动从输出 JSON 中剔除。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param imageBase64 base64 编码的图像数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TencentOcrPayload(
        @JsonProperty("ImageBase64") String imageBase64) {

    /** 构造腾讯云 OCR base64 模式请求载荷。 */
    public static TencentOcrPayload of(String imageBase64) {
        return new TencentOcrPayload(imageBase64);
    }
}
