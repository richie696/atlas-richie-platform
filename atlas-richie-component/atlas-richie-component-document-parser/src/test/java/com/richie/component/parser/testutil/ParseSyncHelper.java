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
package com.richie.component.parser.testutil;

import com.richie.component.parser.DocumentParser;
import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.exception.DocumentParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试 helper — 将 {@link DocumentParser#parseStream} 的事件流收集为 {@link ParsedDocument}。
 * <p>
 * v1.0 收尾后 SPI 不再提供同步 {@code parse} 入口, 测试用例需要自己跑一次 stream 收集。
 */
public final class ParseSyncHelper {

    private ParseSyncHelper() {
    }

    public static ParsedDocument collect(DocumentParser parser, ParserSource source, ParserContext ctx) {
        List<DocumentSegment> collected = new ArrayList<>();
        ParsedDocument[] summaryHolder = new ParsedDocument[1];
        int[] totalImages = {0};
        Throwable[] failure = {null};
        parser.parseStream(source, ctx, event -> {
            switch (event) {
                case ParseEvent.Streaming s -> collected.add(s.segment());
                case ParseEvent.ImageStreaming ignored -> totalImages[0]++;
                case ParseEvent.Finished f -> summaryHolder[0] = f.summary();
                case ParseEvent.Failed err -> failure[0] = err.error();
            }
        });
        if (failure[0] instanceof DocumentParseException dpe) {
            throw dpe;
        }
        if (failure[0] != null) {
            throw new DocumentParseException("Parse failed", failure[0]);
        }
        if (summaryHolder[0] != null) {
            return summaryHolder[0];
        }
        return new ParsedDocument(null, null, collected, Map.of("totalImages", totalImages[0]));
    }
}
