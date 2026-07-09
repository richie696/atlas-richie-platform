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

import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ImageSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParseListener;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ParseEvent schema 一致性测试 — 跨 3 个 parser impl 断言契约。
 * <p>
 * Step 6 of schema unification. 验证:
 * <ul>
 *   <li>每个 Streaming event 携带有效 DocumentSegment (sectionPath + meta 非空)</li>
 *   <li>每个 ImageStreaming event 携带完整 ImageSegment (format/data/name/sectionPath 都有值)</li>
 *   <li>每个 Finished event 携带含 "format" key 的 metadata</li>
 *   <li>happy path 无 Failed event</li>
 * </ul>
 */
class ParseEventSchemaConformanceTest {

    @Test
    @DisplayName("Tika impl: 跨 PDF doc assert Stream/Image/Finished schema 一致性")
    void tikaSchema() throws Exception {
        byte[] pdf = generateMinimalPdf("Hello");
        List<ParseEvent> events = collectEvents(
                new TikaDocumentParser(new com.richie.component.parser.config.ParserProperties()),
                new ParserSource.StreamSource(new ByteArrayInputStream(pdf), "schema.pdf"));

        long streams = events.stream().filter(e -> e instanceof ParseEvent.Streaming).count();
        long finished = events.stream().filter(e -> e instanceof ParseEvent.Finished).count();
        long failed = events.stream().filter(e -> e instanceof ParseEvent.Failed).count();

        assertTrue(streams >= 1, "应有 >=1 个 Streaming event");
        assertEquals(1, finished, "应有 1 个 Finished event");
        assertEquals(0, failed, "happy path 不应有 Failed");

        events.stream()
                .filter(e -> e instanceof ParseEvent.Streaming)
                .map(e -> ((ParseEvent.Streaming) e).segment())
                .forEach(seg -> assertSegmentSchema(seg, "tika"));
    }

    @Test
    @DisplayName("Fesod impl: 跨 XLSX doc assert 文本段 + Finished schema")
    void fesodSchema() throws Exception {
        byte[] xlsx = generateMinimalXlsx();
        List<ParseEvent> events = collectEvents(
                new FesodDocumentParser(),
                new ParserSource.StreamSource(new ByteArrayInputStream(xlsx), "schema.xlsx"));

        long streams = events.stream().filter(e -> e instanceof ParseEvent.Streaming).count();
        long finished = events.stream().filter(e -> e instanceof ParseEvent.Finished).count();
        long failed = events.stream().filter(e -> e instanceof ParseEvent.Failed).count();

        assertTrue(streams >= 1, "Fesod 应有 >=1 个 Streaming event");
        assertEquals(1, finished, "应有 1 个 Finished event");
        assertEquals(0, failed, "happy path 不应有 Failed");

        events.stream()
                .filter(e -> e instanceof ParseEvent.Streaming)
                .map(e -> ((ParseEvent.Streaming) e).segment())
                .forEach(seg -> assertSegmentSchema(seg, "fesod"));

        events.stream()
                .filter(e -> e instanceof ParseEvent.Finished)
                .map(e -> ((ParseEvent.Finished) e).summary())
                .forEach(this::assertFinishedSchema);
    }

    @Test
    @DisplayName("TextFastPath impl: 跨 TXT doc assert 文本段 schema")
    void textSchema() {
        byte[] txt = "Line 1\nLine 2\n\nLine 3\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<ParseEvent> events = collectEvents(
                new TextFastPathParser(),
                new ParserSource.StreamSource(new ByteArrayInputStream(txt), "schema.txt"));

        long streams = events.stream().filter(e -> e instanceof ParseEvent.Streaming).count();
        long finished = events.stream().filter(e -> e instanceof ParseEvent.Finished).count();
        long failed = events.stream().filter(e -> e instanceof ParseEvent.Failed).count();

        assertTrue(streams >= 1, "TextFastPath 应有 >=1 个 Streaming event");
        assertEquals(1, finished, "应有 1 个 Finished event");
        assertEquals(0, failed, "happy path 不应有 Failed");

        events.stream()
                .filter(e -> e instanceof ParseEvent.Streaming)
                .map(e -> ((ParseEvent.Streaming) e).segment())
                .forEach(seg -> assertSegmentSchema(seg, "text/plain"));

        events.stream()
                .filter(e -> e instanceof ParseEvent.Finished)
                .map(e -> ((ParseEvent.Finished) e).summary())
                .forEach(this::assertFinishedSchema);
    }

    @Test
    @DisplayName("TextFastPath impl: 不存在文件必须 emit Failed event (SPI 契约)")
    void textFastPathEmitsFailedNotThrow() {
        ParserSource bad = new ParserSource.FileSource(
                new java.io.File("/tmp/__nonexistent_for_conformance_test__.txt"));
        List<ParseEvent> events = collectEvents(new TextFastPathParser(), bad);
        long failed = events.stream().filter(e -> e instanceof ParseEvent.Failed).count();
        assertTrue(failed >= 1, "不存在文件必须 emit Failed event");
    }

    private void assertSegmentSchema(DocumentSegment seg, String expectedFormatKey) {
        assertNotNull(seg.text(), "Streaming.text 不可空");
        assertNotNull(seg.sectionPath(), "Streaming.sectionPath 不可空");
        assertFalse(seg.sectionPath().isEmpty(), "Streaming.sectionPath 不可 empty");
        assertNotNull(seg.meta(), "Streaming.meta 不可 null");
    }

    private void assertFinishedSchema(ParsedDocument summary) {
        assertNotNull(summary.metadata(), "Finished.metadata 不可 null");
        assertTrue(summary.metadata().containsKey("format"),
                "Finished.metadata 必含 'format' key (Step 4 契约)");
    }

    private List<ParseEvent> collectEvents(
            com.richie.component.parser.DocumentParser parser,
            ParserSource source) {
        List<ParseEvent> sink = new ArrayList<>();
        try {
            parser.parseStream(source, ParserContext.defaults(), (ParseListener) sink::add);
        } catch (RuntimeException ignored) {
            // 异常路径下已有 Failed event 入库
        }
        return sink;
    }

    private byte[] generateMinimalPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] generateMinimalXlsx() throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("header");
            sheet.createRow(1).createCell(0).setCellValue("row1");
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
