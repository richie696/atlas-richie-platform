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

/**
 * 文档解析器 SPI — 流式异步接口。
 * <p>
 * 业务方调用 {@link DocumentReader#readStreaming} 时,内部根据格式路由到对应实现。
 * 各实现按以下模式工作:
 * <ol>
 *   <li>解析过程中每发现一段文本 → emit {@code ParseEvent.Streaming}</li>
 *   <li>每发现一张图片 → emit {@code ParseEvent.ImageStreaming} (原样返回字节, 不做 OCR)</li>
 *   <li>全部解析完成 → emit {@code ParseEvent.Finished} (含汇总 ParsedDocument)</li>
 *   <li>解析失败 → emit {@code ParseEvent.Failed} (含异常)</li>
 * </ol>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public interface DocumentParser {

    /**
     * 流式解析接口 — 各 parser 必须实现。
     * <p>
     * 实现要求:
     * <ul>
     *   <li>解析过程中逐段 emit {@code ParseEvent.Streaming}</li>
     *   <li>发现图片时 emit {@code ParseEvent.ImageStreaming} (原样返回字节)</li>
     *   <li>解析完成后 emit {@code ParseEvent.Finished}</li>
     *   <li>解析失败时 emit {@code ParseEvent.Failed}</li>
     * </ul>
     * 实现内部应捕获异常 → emit Failed event,而不是直接抛出 (除非无法恢复的 fatal error)。
     */
    void parseStream(ParserSource source, ParserContext ctx, ParseListener listener);
}
