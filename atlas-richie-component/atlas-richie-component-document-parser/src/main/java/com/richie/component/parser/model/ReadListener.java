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
package com.richie.component.parser.model;

/**
 * 公开流式事件订阅接口 — 业务方通过 {@link com.richie.component.parser.DocumentReader#readStreaming} 接收 {@link ReadEvent} 流。
 */
@FunctionalInterface
public interface ReadListener {

    void onEvent(ReadEvent event);
}
