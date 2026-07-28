/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.parser.model;

import java.util.Map;

/**
 * 公开流式读取完成摘要。
 *
 * <p>不包含 {@link ReadResult#sections()} 或 {@link ReadResult#images()}，以保证大文档的
 * {@link ReadEvent.Finished} 是常量空间事件。</p>
 */
public record ReadSummary(String title, String author, Map<String, Object> metadata) {

    public ReadSummary {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
