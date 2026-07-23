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
 * R-N 多模态 — 腾讯混元(Hunyuan)语音适配器。
 *
 * <p>本包属于 atlas-richie-component-ai 的多模态能力(R-N)实现,与 Chat 时代的老代码
 * (com.richie.component.ai.service / model / config)并列存在,未来按 R-N-DESIGN.md §4
 * 计划在 ai-hunyuan 子模块内注册 3 个 VendorConfig(tokenhub/tts/stt),差异收敛到
 * {@code Tc3Signer}(BearerStsSigner + Tc3StsSigner)。
 *
 * <h2>覆盖能力(本包)</h2>
 * <ul>
 *   <li><b>TTS</b> — 独立产品 1073({@code tts.tencentcloudapi.com}),TC3-HMAC-SHA256 签名,base64 输出,
 *       Android/iOS/HarmonyOS/Flutter SDK(消费模型:F/H 前端直连为主,服务端 impl 为可选)</li>
 *   <li><b>STT</b> — 独立产品 1093({@code asr.tencentcloudapi.com}),TC3 签名,三模式:
 *       一句话同步 / 录音文件异步 / 语音流 WS</li>
 * </ul>
 *
 * <p>Chat/Embedding 经 TokenHub(产品 1823)Bearer,不在本包内。
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>单测:9/9 通过(R-N-DESIGN.md §11)</li>
 *   <li>Caveat 14 IT 真机验证:<b>待跑</b>(尤其 STT 三模式差异)</li>
 * </ul>
 *
 * @author R-N Multimodal Sprint
 * @since 0.2 (2026-07-20)
 */
package com.richie.component.ai.provider.hunyuan;