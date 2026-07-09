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
package com.richie.component.parser;

import com.richie.component.parser.exception.DocumentParseException;

/**
 * 解析监听器 — 业务方实现此接口接收 {@link ParseEvent} 事件流。
 * <p>
 * 使用示例:
 * <pre>{@code
 * parser.parseStream(source, event -> {
 *     switch (event) {
 *         case ParseEvent.Streaming s ->
 *             embeddingService.embed(s.segment().text());
 *         case ParseEvent.ImageStreaming i ->
 *             ocrService.extract(i.image().data());  // 或存图/忽略
 *         case ParseEvent.Finished f ->
 *             log.info("done: {} segs, {} imgs", f.totalSegments(), f.totalImages());
 *         case ParseEvent.Failed err ->
 *             log.error("parse failed", err.error());
 *     }
 * });
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
@FunctionalInterface
public interface ParseListener {

    /**
     * 接收所有 {@link ParseEvent} 事件。
     * <p>
     * 默认实现会转发到 4 个具体方法 (onStreaming / onImage / onFinished / onError)。
     * 业务方只需重写感兴趣的 default 方法,其他用默认 no-op 即可。
     */
    void onEvent(ParseEvent event);

    /** 文本段落事件回调 */
    default void onStreaming(DocumentSegment segment, String sourceName) {
        // 默认 no-op — 调用方按需重写
    }

    /** 图片资源事件回调 */
    default void onImage(ImageSegment image, String sourceName) {
        // 默认 no-op — 调用方按需重写 (OCR / 存图 / 忽略)
    }

    /** 完成事件回调 */
    default void onFinished(ParsedDocument summary, int totalSegments,
                            int totalImages, String sourceName) {
        // 默认 no-op
    }

    /** 失败事件回调 */
    default void onError(DocumentParseException error, String sourceName) {
        // 默认 no-op (异常仍可通过 facade 抛出,这里只是事件通知)
    }
}
