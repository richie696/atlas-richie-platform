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
 * R-N 多模态 — 华为盘古(Pangu)适配器。
 *
 * <p>本包属于 atlas-richie-component-ai 的多模态能力(R-N)实现,与 Chat 时代的老代码
 * (com.richie.component.ai.service / model / config)并列存在,未来按 R-N-DESIGN.md §4
 * 计划拆分为独立 Maven 子模块 ai-pangu(原 ai-huawei-sis 非大模型已剥离)。
 *
 * <h2>覆盖能力</h2>
 * <ul>
 *   <li><b>TTS</b> — 盘古语音合成,认证 {@code X-Apig-AppCode}(<b>端点 URL 在单测阶段为假设</b>,
 *       需 IT 真机校验)</li>
 *   <li><b>STT</b> — 盘古语音识别,认证同 TTS</li>
 *   <li><b>Rerank</b> — 盘古 Rerank REST,端点 {@code /api/v1/rerank},
 *       鉴权 {@code X-Apig-AppCode}(与本包语音端共用 {@link com.richie.component.ai.provider.sign.AppCodeSigner}),
 *       请求使用 {@code top_k} 字段命名而非 {@code top_n}</li>
 * </ul>
 *
 * <p>华为云独立语音服务(SIS)在 {@code com.richie.component.ai.huawei.sis} 包。
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>单测:已通过(R-N-DESIGN.md §11)</li>
 *   <li>Caveat 14 IT 真机验证:<b>待跑</b>(<b>端点路径为假设,优先级最高</b>)</li>
 * </ul>
 *
 * @author R-N Multimodal Sprint
 * @since 0.2 (2026-07-20)
 */
package com.richie.component.ai.provider.pangu;