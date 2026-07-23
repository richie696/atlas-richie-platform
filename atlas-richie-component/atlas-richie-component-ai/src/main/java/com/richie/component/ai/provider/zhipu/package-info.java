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
 * R-N 多模态 — 智谱(Zhipu / 智谱清言)适配器。
 *
 * <p>本包属于 atlas-richie-component-ai 的多模态能力(R-N)实现,与 Chat 时代的老代码
 * (com.richie.component.ai.service / model / config)并列存在,未来按 R-N-DESIGN.md §4
 * 计划拆分为独立 Maven 子模块 ai-zhipu。
 *
 * <h2>覆盖能力</h2>
 * <ul>
 *   <li><b>TTS</b> — glm-tts,端点 {@code /paas/v4/audio/speech},Bearer 认证,7 音色</li>
 *   <li><b>STT</b> — glm-asr-2512,端点 {@code /paas/v4/audio/transcriptions},Bearer 认证,multipart + JSON base64 双路径</li>
 *   <li><b>Rerank</b> — glm-rerank,端点 {@code /paas/v4/rerank},Bearer 认证,
 *       文档≤128 条,响应 Cohere shape</li>
 * </ul>
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>单测:已通过(R-N-DESIGN.md §11)</li>
 *   <li>Caveat 14 IT 真机验证:<b>待跑</b>(尤其 STT multipart 路径)</li>
 * </ul>
 *
 * @author R-N Multimodal Sprint
 * @since 0.2 (2026-07-20)
 */
package com.richie.component.ai.provider.zhipu;