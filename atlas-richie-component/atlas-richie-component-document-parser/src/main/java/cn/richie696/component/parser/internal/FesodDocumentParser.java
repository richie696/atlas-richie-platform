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
package cn.richie696.component.parser.internal;

import cn.richie696.component.parser.DocumentParser;
import cn.richie696.component.parser.DocumentSegment;
import cn.richie696.component.parser.DocumentSummary;
import cn.richie696.component.parser.ParseEvent;
import cn.richie696.component.parser.ParseListener;
import cn.richie696.component.parser.ParserContext;
import cn.richie696.component.parser.ParserSource;
import cn.richie696.component.parser.exception.DocumentParseException;
import org.apache.fesod.sheet.ExcelReader;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

/**
 * Apache Fesod Excel 解析器 — 流式 emit 文本。
 * <p>
 * Fesod 按行消费输入流并立即 emit，绝不为图片提取保留整份 Excel 字节。图片提取应由独立的
 * 可重开源处理阶段完成，避免破坏文本 RAG 管道的内存上界。
 * <p>
 * 不重写 {@code invokeHead} — Fesod 默认实现为空,避免与 {@code ReadListener<Map<Integer,
 * ReadCellData<?>>>} 默认 {@code invokeHead} 签名擦除冲突。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class FesodDocumentParser implements DocumentParser {

    public FesodDocumentParser() {
    }

    @Override
    public void parseStream(ParserSource source, ParserContext ctx, ParseListener listener) {
        try {
            String nameHint = source.nameHint();

            int[] sheetCount = {0};
            int[] totalRows = {0};

            ReadListener<Map<Integer, String>> textListener = new ReadListener<>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    totalRows[0]++;
                    if (data == null || data.isEmpty()) return;
                    StringBuilder sb = new StringBuilder();
                    data.forEach((colIdx, value) -> {
                        if (value != null && !value.isEmpty()) {
                            if (!sb.isEmpty()) sb.append(' ');
                            sb.append(value);
                        }
                    });
                    String text = sb.toString().trim();
                    if (text.isEmpty()) return;
                    DocumentSegment seg = buildSegment(context, text);
                    listener.onEvent(new ParseEvent.Streaming(seg));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    if (context != null && context.readSheetHolder() != null) {
                        sheetCount[0]++;
                    }
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("format", "fesod");
                    meta.put("sheetCount", sheetCount[0]);
                    meta.put("totalRows", totalRows[0]);
                    listener.onEvent(new ParseEvent.Finished(
                            new DocumentSummary(null, null, meta), totalRows[0], 0));
                }
            };

            try (InputStream in = openSource(source);
                 ExcelReader reader = FesodSheet.read(in,
                    textListener).headRowNumber(0).build()) {
                reader.readAll();
            }
        } catch (DocumentParseException e) {
            listener.onEvent(new ParseEvent.Failed(e));
        } catch (java.io.IOException e) {
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("Fesod input close failed: " + source.nameHint(), e)));
        } catch (RuntimeException e) {
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("Fesod parse failed: " + source.nameHint(), e)));
        }
    }

    private InputStream openSource(ParserSource source) {
        return switch (source) {
            case ParserSource.FileSource f -> {
                try {
                    yield java.nio.file.Files.newInputStream(f.file().toPath());
                } catch (NoSuchFileException e) {
                    throw new DocumentParseException("File not found: " + f.file(), e);
                } catch (java.io.IOException e) {
                    throw new DocumentParseException("Failed to open " + f.file(), e);
                }
            }
            case ParserSource.StreamSource s -> s.in();
            case ParserSource.UrlSource ignored -> throw new DocumentParseException(
                    "FesodDocumentParser does not accept URL source directly");
        };
    }

    private DocumentSegment buildSegment(AnalysisContext context, String text) {
        String sheetName = "(unknown)";
        int rowIndex = 0;
        if (context != null) {
            if (context.readSheetHolder() != null
                    && context.readSheetHolder().getSheetName() != null) {
                sheetName = context.readSheetHolder().getSheetName();
            }
            if (context.readRowHolder() != null
                    && context.readRowHolder().getRowIndex() != null) {
                rowIndex = context.readRowHolder().getRowIndex();
            }
        }
        String sectionPath = "/" + sheetName + "/Row[" + (rowIndex + 1) + "]";
        Map<String, Object> meta = new HashMap<>();
        meta.put("format", "fesod");
        meta.put("sheet", sheetName);
        meta.put("row", rowIndex + 1);
        return new DocumentSegment(text, null, sectionPath, meta);
    }

}
