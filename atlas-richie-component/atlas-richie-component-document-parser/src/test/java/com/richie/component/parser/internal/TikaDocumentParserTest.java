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
import com.richie.component.parser.testutil.ParseSyncHelper;

import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ImageSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.config.ParserProperties;
import com.richie.component.parser.exception.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TikaDocumentParser} 单元测试 — PDF 解析 + 全图片 PDF 检测。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser(new ParserProperties());

    @Test
    @DisplayName("Text-based PDF should be parsed into segments")
    void textPdfParsed(@TempDir Path tempDir) throws IOException {
        // Use a longer text so the image-only heuristic is not triggered
        // (default minTextChars = 200, and "Hello World" alone produces < 20 chars)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("This is paragraph ").append(i).append(" of a sample PDF document. ");
        }
        Path pdfFile = createTextPdf(tempDir, sb.toString());
        ParsedDocument doc = ParseSyncHelper.collect(parser,
                new ParserSource.FileSource(pdfFile.toFile()),
                ParserContext.defaults());
        assertNotNull(doc);
        List<DocumentSegment> segments = doc.segments();
        assertNotNull(segments);
        assertTrue(segments.size() >= 1, "should produce at least 1 segment");
        // Concatenated text should contain our marker
        String allText = segments.stream().map(DocumentSegment::text).reduce("", String::concat);
        assertTrue(allText.contains("This is paragraph"), "extracted text should contain marker");
        assertTrue(allText.length() > 200, "extracted text should exceed image-only threshold");
    }

    @Test
    @DisplayName("Image-only PDF with strict mode enabled should throw ImageOnlyPdfException")
    void imageOnlyPdf_strictModeEnabledThrows(@TempDir Path tempDir) throws IOException {
        // Build a parser with image-only-detection strict mode enabled
        ParserProperties strictProps = new ParserProperties();
        strictProps.getPdf().getImageOnlyDetection().setEnabled(true);
        strictProps.getPdf().getImageOnlyDetection().setMinTextChars(200);
        strictProps.getPdf().getImageOnlyDetection().setMinImageCount(5);
        TikaDocumentParser strictParser = new TikaDocumentParser(strictProps);

        Path pdfFile = createImageOnlyPdf(tempDir);
        List<ParseEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        strictParser.parseStream(
                new ParserSource.FileSource(pdfFile.toFile()),
                ParserContext.defaults(),
                events::add);

        ParseEvent.Failed failed = events.stream()
                .filter(e -> e instanceof ParseEvent.Failed)
                .map(e -> (ParseEvent.Failed) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected Failed event in strict mode, got " + events));
        assertTrue(failed.error() instanceof com.richie.component.parser.exception.ImageOnlyPdfException,
                "expected ImageOnlyPdfException, got " + failed.error().getClass());
    }

    @Test
    @DisplayName("Image-only PDF should NOT throw (default behavior is emit events)")
    void imageOnlyPdfEmitsImageStreaming(@TempDir Path tempDir) throws IOException {
        Path pdfFile = createImageOnlyPdf(tempDir);
        // Phase 8d: image-only PDFs no longer throw by default — they emit
        // ImageStreaming events (when Tika wraps images in <img>) or a synthetic
        // placeholder (when Tika emits raw XObjects). The test accepts any non-empty
        // event stream — the exact count varies by Tika's rendering of synthetic PDFs.
        List<ParseEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        parser.parseStream(
                new ParserSource.FileSource(pdfFile.toFile()),
                ParserContext.defaults(),
                events::add);
        assertFalse(events.isEmpty(),
                "parser should emit at least one event for an image-only PDF, got empty list");
        // No Failed event means parse succeeded
        assertTrue(events.stream().noneMatch(e -> e instanceof ParseEvent.Failed),
                "parser should not emit Failed event for a valid PDF, got " + events);
        // Verify a Finished event is present (parse completed)
        long finishedCount = events.stream()
                .filter(e -> e instanceof ParseEvent.Finished)
                .count();
        assertEquals(1, finishedCount,
                "parser should emit exactly one Finished event, got " + events);
    }

    @Test
    @DisplayName("UrlSource should emit Failed event (Phase 5 not yet wired)")
    void urlSourceThrows() throws MalformedURLException {
        ParserSource.UrlSource urlSource = new ParserSource.UrlSource(
                new URL("https://example.com/test.pdf"),
                com.richie.component.parser.UrlFetchPolicy.defaults());
        List<ParseEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        parser.parseStream(urlSource, ParserContext.defaults(), events::add);
        ParseEvent.Failed failed = events.stream()
                .filter(e -> e instanceof ParseEvent.Failed)
                .map(e -> (ParseEvent.Failed) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected Failed event but got " + events));
        assertTrue(failed.error().getMessage().contains("TikaDocumentParser")
                        || failed.error().getMessage().contains("Phase 5"),
                "expected URL rejection, got: " + failed.error().getMessage());
    }

    @Test
    @DisplayName("FileSource with missing file should emit Failed event")
    void fileSourceNotFoundThrows(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist.pdf").toFile();
        List<ParseEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        parser.parseStream(
                new ParserSource.FileSource(missing),
                ParserContext.defaults(),
                events::add);
        ParseEvent.Failed failed = events.stream()
                .filter(e -> e instanceof ParseEvent.Failed)
                .map(e -> (ParseEvent.Failed) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected Failed event but got " + events));
        assertTrue(failed.error().getMessage().contains("File not found"),
                "expected File not found, got: " + failed.error().getMessage());
    }

    @Test
    @DisplayName("StreamSource with empty input should throw DocumentParseException")
    void emptyStreamThrows() {
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> ParseSyncHelper.collect(parser,
                        new ParserSource.StreamSource(
                                new ByteArrayInputStream(new byte[0]), "empty.pdf"),
                        ParserContext.defaults()));
        assertNotNull(ex.getMessage());
    }

    // ============ Fixtures ============

    private static Path createTextPdf(Path tempDir, String text) throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static Path createImageOnlyPdf(Path tempDir) throws IOException {
        Path file = tempDir.resolve("image-only.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            // Create a 1x1 red PNG image in memory and embed it
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, Color.RED.getRGB());
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(img, "png", pngOut);
            byte[] pngBytes = pngOut.toByteArray();

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                    doc, pngBytes, "red-pixel.png");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 50, 700, 200, 200);
                // Deliberately no text content
            }
            doc.save(file.toFile());
        }
        return file;
    }
}
