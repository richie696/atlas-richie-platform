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
package com.richie.component.web.core.hook;

/**
 * 事件总线事件标记接口（README.md §4.5 HookBus）。
 * <p>
 * 任何要通过 {@link HookBus#publish} 派发的事件都必须实现本接口。
 * 实现可使用 {@code record} 或普通类。
 *
 * <h2>约束</h2>
 * <ul>
 *   <li>事件应是<strong>不可变</strong>的（避免订阅者改共享状态）</li>
 *   <li>事件<strong>不应抛</strong>异常（订阅者抛异常由 HookBus 隔离）</li>
 *   <li>事件数据应<strong>轻量</strong>，避免跨线程传递大型对象</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface HookEvent {
    // marker interface — no methods
}