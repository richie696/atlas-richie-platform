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

/**
 * R-N 多模态 — 厂商认证签名工具集(支撑多模态 TTS/STT/Rerank 等能力的凭证签发)。
 *
 * <p>本包属于 atlas-richie-component-ai 的多模态能力(R-N)实现,与 Chat 时代的老代码
 * (com.richie.component.ai.service / model / config)并列存在。纯 JDK crypto,无第三方 SDK 引入。
 *
 * <h2>签名器</h2>
 * <ul>
 *   <li>{@code com.richie.component.ai.support.sign.Tc3Signer} — 腾讯云 TC3-HMAC-SHA256
 *       (用于 Hunyuan 独立产品 1073 TTS / 1093 STT)</li>
 *   <li>{@code com.richie.component.ai.support.sign.Aws4Signer} — AWS4 风格 HMAC
 *       (用于部分华为云 SIS 端点的请求级签名)</li>
 *   <li>{@code com.richie.component.ai.support.sign.AppCodeSigner} — 华为云 Apig-AppCode
 *       轻量 header 签发(用于 Pangu / Huawei-SIS 的 Apig 网关类端点)</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>F/G/H:WS 数据流走前端直连,中台仅做凭证签发(STS);本包是中台侧的"凭证签发支撑层"</li>
 *   <li>I:全部走 {@code HttpClient} 的 CompletableFuture 模式,不依赖 vendor SDK 全量引入</li>
 * </ul>
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>单测:已通过(R-N-DESIGN.md §11)</li>
 *   <li>Caveat 14 IT 真机验证:<b>待跑</b>(尤其 TC3 签名对 Hunyuan 1073/1093 真实响应)</li>
 * </ul>
 *
 * @author R-N Multimodal Sprint
 * @since 0.2 (2026-07-20)
 */
package com.richie.component.ai.provider.sign;