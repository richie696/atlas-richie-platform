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

import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.testutil.ParseSyncHelper;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.exception.DocumentParseException;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FesodDocumentParser} 单元测试 — xlsx 流式解析。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class FesodDocumentParserTest {

    private final FesodDocumentParser parser = new FesodDocumentParser();

    @Test
    @DisplayName("Simple xlsx should be parsed into segments with sheet metadata")
    void simpleXlsxParsed(@TempDir Path tempDir) {
        Path xlsxFile = createSimpleXlsx(tempDir);
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(xlsxFile.toFile()),
                ParserContext.defaults());

        assertNotNull(doc);
        List<DocumentSegment> segments = doc.segments();
        assertNotNull(segments);
        // 1 header segment + 2 data rows = 3
        assertEquals(3, segments.size(),
                "should produce 1 header + 2 data row segments");

        // First segment should be the header
        DocumentSegment header = segments.get(0);
        assertTrue(header.text().contains("Name") && header.text().contains("Age"),
                "header should contain 'Name' and 'Age'");

        // Subsequent segments should contain data
        boolean hasAlice = segments.stream().anyMatch(s -> s.text().contains("Alice"));
        boolean hasBob = segments.stream().anyMatch(s -> s.text().contains("Bob"));
        assertTrue(hasAlice, "should contain 'Alice' row");
        assertTrue(hasBob, "should contain 'Bob' row");

        // Section paths should be under the sheet name
        for (DocumentSegment seg : segments) {
            assertNotNull(seg.sectionPath());
            assertTrue(seg.sectionPath().startsWith("/"),
                    "sectionPath should be hierarchical");
        }
    }

    @Test
    @DisplayName("Empty xlsx should produce zero segments")
    void emptyXlsxProducesZeroSegments(@TempDir Path tempDir) {
        Path xlsxFile = createEmptyXlsx(tempDir);
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(xlsxFile.toFile()),
                ParserContext.defaults());
        assertNotNull(doc);
        assertEquals(0, doc.segments().size());
    }

    @Test
    @DisplayName("Multi-sheet xlsx should produce segments from all sheets")
    void multiSheetXlsxProducesAllSheets(@TempDir Path tempDir) {
        Path xlsxFile = createMultiSheetXlsx(tempDir);
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(xlsxFile.toFile()),
                ParserContext.defaults());
        assertNotNull(doc);
        List<DocumentSegment> segments = doc.segments();
        assertTrue(segments.size() >= 4,
                "should have segments from both sheets (2 headers + 2 data rows)");

        boolean hasSheet1 = segments.stream().anyMatch(
                s -> s.sectionPath() != null && s.sectionPath().contains("Sheet1"));
        boolean hasSheet2 = segments.stream().anyMatch(
                s -> s.sectionPath() != null && s.sectionPath().contains("Sheet2"));
        assertTrue(hasSheet1, "should contain segments from Sheet1");
        assertTrue(hasSheet2, "should contain segments from Sheet2");
    }

    @Test
    @DisplayName("UrlSource should throw DocumentParseException (Phase 5 not yet wired)")
    void urlSourceThrows() throws MalformedURLException {
        ParserSource.UrlSource urlSource = new ParserSource.UrlSource(
                new URL("https://example.com/test.xlsx"),
                com.richie.component.parser.UrlFetchPolicy.defaults());
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> ParseSyncHelper.collect(parser,urlSource, ParserContext.defaults()));
        assertTrue(ex.getMessage().contains("FesodDocumentParser")
                || ex.getMessage().contains("Phase 5"));
    }

    @Test
    @DisplayName("FileSource with missing file should throw DocumentParseException")
    void fileSourceNotFoundThrows(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist.xlsx").toFile();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> ParseSyncHelper.collect(parser,new ParserSource.FileSource(missing), ParserContext.defaults()));
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("docMeta should expose sheetCount and totalRows")
    void docMetaExposesCounts(@TempDir Path tempDir) {
        Path xlsxFile = createSimpleXlsx(tempDir);
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(xlsxFile.toFile()),
                ParserContext.defaults());
        assertEquals("fesod", doc.metadata().get("format"));
        assertEquals(1, doc.metadata().get("sheetCount"));
        assertEquals(3, doc.metadata().get("totalRows"),
                "totalRows should include header + 2 data rows");
    }

    // ============ Fixtures ============

    private static Path createSimpleXlsx(Path tempDir) {
        Path file = tempDir.resolve("sample.xlsx");
        List<List<String>> data = List.of(
                List.of("Name", "Age"),
                List.of("Alice", "30"),
                List.of("Bob", "25"));
        FesodSheet.write(file.toFile()).sheet().doWrite(data);
        return file;
    }

    private static Path createEmptyXlsx(Path tempDir) {
        Path file = tempDir.resolve("empty.xlsx");
        // An empty list still creates a valid (empty) xlsx file
        FesodSheet.write(file.toFile()).sheet().doWrite(new ArrayList<>());
        return file;
    }

    private static Path createMultiSheetXlsx(Path tempDir) {
        Path file = tempDir.resolve("multi-sheet.xlsx");
        try (org.apache.fesod.sheet.ExcelWriter writer =
                     FesodSheet.write(file.toFile()).build()) {
            org.apache.fesod.sheet.write.metadata.WriteSheet sheet1 =
                    FesodSheet.writerSheet("Sheet1").build();
            writer.write(
                    List.of(List.of("Col1", "Col2"), List.of("a1", "a2")),
                    sheet1);
            org.apache.fesod.sheet.write.metadata.WriteSheet sheet2 =
                    FesodSheet.writerSheet("Sheet2").build();
            writer.write(
                    List.of(List.of("Col1", "Col2"), List.of("b1", "b2")),
                    sheet2);
        }
        return file;
    }
}
