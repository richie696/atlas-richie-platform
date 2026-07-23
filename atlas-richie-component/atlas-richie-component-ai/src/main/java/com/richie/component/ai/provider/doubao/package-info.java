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
 * R-N 多模态 — 豆包 / 火山(Doubao / Volcengine)语音适配器。
 *
 * <p>本包属于 atlas-richie-component-ai 的多模态能力(R-N)实现,与 Chat 时代的老代码
 * (com.richie.component.ai.service / model / config)并列存在,未来按 R-N-DESIGN.md §4
 * 计划拆分为独立 Maven 子模块 ai-doubao。
 *
 * <h2>覆盖能力</h2>
 * <ul>
 *   <li><b>TTS</b> — openspeech V3 单向 SSE + WS 双向,端点 {@code openspeech.bytedance.com/api/v3/tts/unidirectional},
 *       325+ 音色;认证 {@code X-Api-Key} / {@code X-Api-App-Id} + {@code X-Api-Resource-Id}(seed-tts-2.0 / seed-icl-2.0);
 *       NDJSON base64 分片</li>
 *   <li><b>STT</b> — 一句话 flash / 文件异步 submit+query / WS 流式,
 *       认证同 TTS;Resource-Id 区分(如 {@code volc.bigasr.auc_turbo})</li>
 * </ul>
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>单测:已通过(R-N-DESIGN.md §11)</li>
 *   <li>Caveat 14 IT 真机验证:<b>待跑</b>(尤其 {@code X-Api-Resource-Id} 资源 ID 区分)</li>
 * </ul>
 *
 * @author R-N Multimodal Sprint
 * @since 0.2 (2026-07-20)
 */
package com.richie.component.ai.provider.doubao;