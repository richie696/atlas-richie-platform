/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.hook;

/**
 * 事件订阅者（README.md §4.5 HookBus）。
 * <p>
 * 订阅者按事件类型注册到 {@link HookBus}，每种事件类型可有多个订阅者。
 * 订阅者<strong>不应抛</strong>异常——若抛，HookBus 应隔离并记录。
 *
 * @author richie696
 * @since 2026-07
 */
@FunctionalInterface
public interface HookSubscriber<E extends HookEvent> {

    /**
     * 接收事件。实现应保证非阻塞 + 异常隔离（用 try/catch 包业务逻辑）。
     */
    void onEvent(E event);
}