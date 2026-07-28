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
package cn.richie696.component.parser;

import java.util.Map;

/**
 * 流式解析完成摘要。
 *
 * <p>刻意不包含文本段和图片列表。这样 {@link ParseEvent.Finished} 不会因为大文档重新把已经
 * 流式发出的内容全部累积到内存。需要完整结果的同步门面由 {@link DocumentReader} 在订阅期间自行聚合。</p>
 */
public record DocumentSummary(String title, String author, Map<String, Object> metadata) {

    public DocumentSummary {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
