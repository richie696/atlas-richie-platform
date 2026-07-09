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
package com.richie.component.parser.internal;

import com.richie.component.parser.DocumentParser;
import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ImageSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParseListener;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.exception.DocumentParseException;
import org.apache.fesod.sheet.ExcelReader;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apache Fesod Excel 解析器 — 流式 emit 文本 + POI 提取嵌入图片。
 * <p>
 * Fesod 解析文本流后会消费流,无法再用 POI 读图。{@link #parseStream} 入口先把流完整读入
 * 内存,然后用同一份 bytes 同时驱动 Fesod 解析 + POI 图片提取。
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
            byte[] bytes = readAllBytes(source);
            String nameHint = source.nameHint();

            int[] sheetCount = {0};
            int[] totalRows = {0};
            List<DocumentSegment> collected = new ArrayList<>();

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
                    collected.add(seg);
                    listener.onEvent(new ParseEvent.Streaming(seg));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    if (context != null && context.readSheetHolder() != null) {
                        sheetCount[0]++;
                    }
                    int totalImages = extractImages(bytes, nameHint, listener);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("format", "fesod");
                    meta.put("sheetCount", sheetCount[0]);
                    meta.put("totalRows", totalRows[0]);
                    ParsedDocument summary = new ParsedDocument(null, null, collected, meta);
                    listener.onEvent(new ParseEvent.Finished(summary, collected.size(), totalImages));
                }
            };

            try (ExcelReader reader = FesodSheet.read(new ByteArrayInputStream(bytes),
                    textListener).headRowNumber(0).build()) {
                reader.readAll();
            }
        } catch (DocumentParseException e) {
            listener.onEvent(new ParseEvent.Failed(e));
        } catch (RuntimeException e) {
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("Fesod parse failed: " + source.nameHint(), e)));
        }
    }

    private byte[] readAllBytes(ParserSource source) {
        try {
            return switch (source) {
                case ParserSource.FileSource f -> {
                    if (!f.file().exists()) {
                        throw new DocumentParseException("File not found: " + f.file());
                    }
                    yield java.nio.file.Files.readAllBytes(f.file().toPath());
                }
                case ParserSource.StreamSource s -> {
                    try (InputStream in = s.in()) {
                        yield in.readAllBytes();
                    }
                }
                case ParserSource.UrlSource ignored ->
                        throw new DocumentParseException(
                                "FesodDocumentParser does not accept URL source directly. "
                                        + "UrlFetcher must download the URL into a stream first (Phase 5)."
                        );
            };
        } catch (IOException e) {
            throw new DocumentParseException(
                    "Failed to read bytes from " + source.nameHint(), e);
        }
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
        meta.put("sheet", sheetName);
        meta.put("row", rowIndex + 1);
        return new DocumentSegment(text, null, sectionPath, meta);
    }

    private int extractImages(byte[] bytes, String sourceName, ParseListener listener) {
        // xlsx magic bytes = PK\x03\x04; xls is not supported here
        if (bytes == null || bytes.length < 4
                || bytes[0] != 'P' || bytes[1] != 'K'
                || bytes[2] != 0x03 || bytes[3] != 0x04) {
            return 0;
        }
        int count = 0;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (int sheetIdx = 0; sheetIdx < wb.getNumberOfSheets(); sheetIdx++) {
                XSSFSheet sheet = wb.getSheetAt(sheetIdx);
                if (sheet == null) continue;
                XSSFDrawing drawing = sheet.getDrawingPatriarch();
                if (drawing == null) continue;
                for (XSSFShape shape : drawing.getShapes()) {
                    if (!(shape instanceof XSSFPicture pic)) continue;
                    XSSFPictureData data = pic.getPictureData();
                    if (data == null) continue;
                    String mime = data.getMimeType();
                    if (mime == null || mime.isBlank()) {
                        mime = "image/octet-stream";
                    }
                    String sectionPath = "/" + sourceName
                            + "/" + sheet.getSheetName()
                            + "/Image[" + (count + 1) + "]";
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("pictureIndex", count);
                    meta.put("sheet", sheet.getSheetName());
                    meta.put("mimeType", mime);
                    String imageName = "Image-" + sheet.getSheetName() + "-" + (count + 1);
                    listener.onEvent(new ParseEvent.ImageStreaming(
                            new ImageSegment(mime, data.getData(), imageName,
                                    null, null, sectionPath, meta)));
                    count++;
                }
            }
        } catch (Exception e) {
            // POI image extraction is best-effort
        }
        return count;
    }
}
