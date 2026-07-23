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
package com.richie.component.ai.support.keypool;

/**
 * API Key 限流检测器 — 从异常中识别"key 需要被 invalidate"。
 *
 * <p>调用方在捕获异常时调 {@link #isKeyInvalidating(Throwable)}:
 * <ul>
 *   <li>true → 池中 invalidate(key),自动换下一个</li>
 *   <li>false → 正常 returnObject(key),异常向上抛</li>
 * </ul>
 *
 * @author richie696
 */
public interface ApiKeyValidator {

    /**
     * 判断异常是否表示"该 key 已被限流 / 无效,应换下一个"。
     *
     * <p>常见情况:
     * <ul>
     *   <li>HTTP 429 Too Many Requests</li>
     *   <li>HTTP 403 Forbidden(可能 = key 失效)</li>
     *   <li>异常消息含 "rate limit" / "quota" / "too many requests" / "exceeded" 等</li>
     * </ul>
     *
     * <p>注意:401 Unauthorized 一般是"key 错误"而非限流 — 不应 invalidate(否则池会一直空)。
     * 但 401 在多 key 池中通常也不应该出现(配置错误),由业务侧 fail-fast 处理。
     */
    boolean isKeyInvalidating(Throwable error);
}