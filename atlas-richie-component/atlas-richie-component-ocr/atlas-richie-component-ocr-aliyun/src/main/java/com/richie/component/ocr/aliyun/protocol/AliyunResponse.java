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
package com.richie.component.ocr.aliyun.protocol;

/**
 * 阿里云读光 OCR 端点返回的协议层响应
 *
 * <p>vendor: {@code aliyun}；API 协议类型: HTTP JSON 同步识别响应；
 * 配置方式: 与 {@code platform.component.ocr.aliyun.*} 指定的端点、模型和鉴权配置对应。</p>
 *
 * @param body     已通过 {@code HttpResponse.bodyAs(AliyunOcrResponse.class)} 反序列化的 envelope，
 *                 非 {@code null} (HTTP 层负责空 body 防御)
 * @param latencyMs 从请求发出到客户端收到响应的墙钟耗时，单位毫秒
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record AliyunResponse(
        AliyunOcrResponse body,
        long latencyMs) {
}
