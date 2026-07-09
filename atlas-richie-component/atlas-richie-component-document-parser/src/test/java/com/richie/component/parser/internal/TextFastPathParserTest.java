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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextFastPathParser} 单元测试 — 覆盖 UTF-8 解析 + 双换行分段 + 异常路径。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class TextFastPathParserTest {

    private final TextFastPathParser parser = new TextFastPathParser();

    @Test
    @DisplayName("UTF-8 text with multiple paragraphs should be split correctly")
    void utf8TextParsed() {
        String content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        ParsedDocument doc = ParseSyncHelper.collect(parser,streamOf(content, "test.txt"), ParserContext.defaults());

        assertNotNull(doc);
        List<DocumentSegment> segments = doc.segments();
        assertEquals(3, segments.size());
        assertEquals("First paragraph.", segments.get(0).text());
        assertEquals("Second paragraph.", segments.get(1).text());
        assertEquals("Third paragraph.", segments.get(2).text());
        assertEquals("text/plain", doc.metadata().get("format"));
        assertEquals("UTF-8", doc.metadata().get("encoding"));
    }

    @Test
    @DisplayName("Paragraphs should split on \\n\\n, \\r\\n\\r\\n, and \\r\\r")
    void paragraphSplitOnDoubleNewline() {
        String content = "A.\n\nB.\r\n\r\nC.\r\rD.";
        ParsedDocument doc = ParseSyncHelper.collect(parser,streamOf(content, "test.txt"), ParserContext.defaults());

        List<DocumentSegment> segments = doc.segments();
        assertEquals(4, segments.size());
        assertEquals("A.", segments.get(0).text());
        assertEquals("B.", segments.get(1).text());
        assertEquals("C.", segments.get(2).text());
        assertEquals("D.", segments.get(3).text());
    }

    @Test
    @DisplayName("Empty content should produce zero segments")
    void emptyContentProducesZeroSegments() {
        ParsedDocument doc = ParseSyncHelper.collect(parser,streamOf("", "empty.txt"), ParserContext.defaults());
        assertNotNull(doc);
        assertTrue(doc.segments().isEmpty());
    }

    @Test
    @DisplayName("Segments should carry order metadata")
    void segmentsCarryOrderMeta() {
        String content = "First.\n\nSecond.\n\nThird.";
        ParsedDocument doc = ParseSyncHelper.collect(parser,streamOf(content, "test.txt"), ParserContext.defaults());

        List<DocumentSegment> segments = doc.segments();
        for (int i = 0; i < segments.size(); i++) {
            Map<String, Object> meta = segments.get(i).meta();
            assertEquals(i, meta.get("order"));
        }
    }

    @Test
    @DisplayName("FileSource should read file from disk")
    void fileSourceReadsFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("readme.txt");
        Files.writeString(file, "Hello, world!\n\nSecond paragraph.");
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(file.toFile()), ParserContext.defaults());
        assertEquals(2, doc.segments().size());
        assertEquals("Hello, world!", doc.segments().get(0).text());
        assertEquals("Second paragraph.", doc.segments().get(1).text());
    }

    @Test
    @DisplayName("FileSource with missing file should throw DocumentParseException")
    void fileSourceNotFoundThrows(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist.txt").toFile();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> ParseSyncHelper.collect(parser,new ParserSource.FileSource(missing), ParserContext.defaults()));
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("UrlSource should throw DocumentParseException (Phase 5 not yet wired)")
    void urlSourceThrows() throws MalformedURLException {
        ParserSource.UrlSource urlSource = new ParserSource.UrlSource(
                new URL("https://example.com/test.txt"),
                com.richie.component.parser.UrlFetchPolicy.defaults());
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> ParseSyncHelper.collect(parser,urlSource, ParserContext.defaults()));
        assertTrue(ex.getMessage().contains("TextFastPathParser")
                || ex.getMessage().contains("Phase 5"));
    }

    @Test
    @DisplayName("supports() should recognize .txt / .md / .markdown")
    void supportsRecognizesTextExtensions() {
        assertTrue(TextFastPathParser.supports("file.txt"));
        assertTrue(TextFastPathParser.supports("FILE.TXT"));
        assertTrue(TextFastPathParser.supports("readme.md"));
        assertTrue(TextFastPathParser.supports("post.markdown"));
        assertTrue(!TextFastPathParser.supports("file.pdf"));
        assertTrue(!TextFastPathParser.supports(null));
        assertTrue(!TextFastPathParser.supports(""));
    }

    @Test
    @DisplayName("Each segment should carry sectionPath")
    void segmentsCarryStreamSectionPath() {
        String content = "Para 1.\n\nPara 2.";
        ParsedDocument doc = ParseSyncHelper.collect(parser,streamOf(content, "notes.md"), ParserContext.defaults());
        assertNotNull(doc);
        List<DocumentSegment> segments = doc.segments();
        assertEquals(2, segments.size());
        assertNotNull(segments.get(0).sectionPath());
        assertNotNull(segments.get(1).sectionPath());
    }

    private static ParserSource.StreamSource streamOf(String content, String nameHint) {
        return new ParserSource.StreamSource(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), nameHint);
    }
}
