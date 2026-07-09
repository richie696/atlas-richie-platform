/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richie.component.parser.internal;

import com.richie.component.parser.DocumentParser;
import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParseListener;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.exception.DocumentParseException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TXT / Markdown fast-path parser — 流式 emit。
 * <p>
 * <b>不走 Tika</b>,直接按 UTF-8 边读行边 emit Streaming event。
 * 遇到空行 (

 / \r
\r
) 即视作段落边界,立即 emit。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class TextFastPathParser implements DocumentParser {

    public TextFastPathParser() {
    }

    @SuppressWarnings("unused")
    @Override
    public void parseStream(ParserSource source, ParserContext ctx, ParseListener listener) {
        try {
            parseInternal(source, listener);
        } catch (DocumentParseException dpe) {
            listener.onEvent(new ParseEvent.Failed(dpe));
        } catch (RuntimeException e) {
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("TextFastPath parse failed: " + source.nameHint(), e)));
        }
    }

    /**
     * 核心流式解析逻辑 — 边读行边 emit Streaming event。
     * <p>
     * 真正的流式: BufferedReader 逐行读,每读完一行就检查段落边界,
     * 边界到来立刻 emit,不等全文读完。
     */
    private void parseInternal(ParserSource source, ParseListener listener) {
        String nameHint = source.nameHint();
        InputStream in = null;
        BufferedReader reader = null;
        try {
            in = openSource(source);
            reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            List<DocumentSegment> collected = new ArrayList<>();
            StringBuilder paragraph = new StringBuilder();
            int order = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!paragraph.isEmpty()) {
                        emitSegment(listener, collected, paragraph.toString().trim(), nameHint, order++);
                        paragraph.setLength(0);
                    }
                } else {
                    if (!paragraph.isEmpty()) {
                        paragraph.append('\n');
                    }
                    paragraph.append(line);
                }
            }
            if (!paragraph.isEmpty()) {
                emitSegment(listener, collected, paragraph.toString().trim(), nameHint, order++);
            }

            // Emit Finished event with summary containing all collected segments.
            Map<String, Object> meta = new HashMap<>();
            meta.put("format", "text/plain");
            meta.put("encoding", "UTF-8");
            ParsedDocument summary = new ParsedDocument(null, null, collected, meta);
            listener.onEvent(new ParseEvent.Finished(summary, collected.size(), 0));
        } catch (IOException e) {
            throw new DocumentParseException(
                    "Failed to read text content from " + nameHint, e);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException ignored) {
                // best-effort
            }
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }

    private void emitSegment(ParseListener listener, List<DocumentSegment> collected,
                             String text, String sourceName,
                             int order) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String sectionPath = "/" + sourceName + "/Paragraph[" + (order + 1) + "]";
        Map<String, Object> meta = new HashMap<>();
        meta.put("order", order);
        DocumentSegment seg = new DocumentSegment(text, null, sectionPath, meta);
        collected.add(seg);
        listener.onEvent(new ParseEvent.Streaming(seg));
    }

    /**
     * 是否支持给定文件名(基于扩展名快速判断)。
     */
    public static boolean supports(String nameHint) {
        if (nameHint == null) {
            return false;
        }
        String lower = nameHint.toLowerCase();
        return lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".markdown");
    }

    private InputStream openSource(ParserSource source) {
        return switch (source) {
            case ParserSource.FileSource f -> openFile(f);
            case ParserSource.StreamSource s -> s.in();
            case ParserSource.UrlSource ignored ->
                    throw new DocumentParseException(
                            "TextFastPathParser does not accept URL source directly. "
                                    + "UrlFetcher must download the URL into a stream first (Phase 5)."
                    );
        };
    }

    private InputStream openFile(ParserSource.FileSource source) {
        try {
            return new FileInputStream(source.file());
        } catch (FileNotFoundException e) {
            throw new DocumentParseException("File not found: " + source.file(), e);
        }
    }
}
