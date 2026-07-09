/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
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

package com.richie.component.parser;

import com.richie.component.parser.config.ParserProperties;
import com.richie.component.parser.internal.FesodDocumentParser;
import com.richie.component.parser.internal.ParserRouter;
import com.richie.component.parser.internal.TikaDocumentParser;
import com.richie.component.parser.internal.UrlFetcher;
import com.sun.net.httpserver.HttpServer;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link DocumentReader} 集成测试 — 端到端覆盖 4 重载入口。
 * <p>
 * 手动构造 reader(避免 Spring Boot 启动开销),@TempDir 生成 fixture 文件。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class DocumentReaderTest {

    private DocumentReader reader;
    private ParserProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ParserProperties();
        properties.getUrl().setAllowPrivateIp(true);
        properties.getUrl().setAllowHttp(true);

        TikaDocumentParser tika = new TikaDocumentParser(properties);
        FesodDocumentParser fesod = new FesodDocumentParser();
        ParserRouter router = new ParserRouter(tika, fesod);
        UrlFetcher urlFetcher = new UrlFetcher();
        reader = new DocumentReader(properties, router, urlFetcher);
    }

    @Test
    @DisplayName("parse(File) should produce segments from a TXT file")
    void parseLocalTextFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, integration test!\n\nSecond paragraph.");
        ParsedDocument doc = reader.parse(file.toFile());
        assertNotNull(doc);
        assertFalse(doc.segments().isEmpty());
        String allText = doc.segments().stream()
                .map(DocumentSegment::text).reduce("", String::concat);
        assertTrue(allText.contains("Hello"));
        assertTrue(allText.contains("Second paragraph"));
        assertEquals("tika", doc.metadata().get("format"));
    }

    @Test
    @DisplayName("parse(File) should produce segments from an xlsx file (routed to Fesod)")
    void parseLocalXlsxFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("sample.xlsx");
        List<List<String>> data = List.of(
                List.of("Name", "Age"),
                List.of("Alice", "30"),
                List.of("Bob", "25"));
        try (org.apache.fesod.sheet.ExcelWriter writer =
                     FesodSheet.write(file.toFile()).build()) {
            org.apache.fesod.sheet.write.metadata.WriteSheet sheet =
                    FesodSheet.writerSheet().build();
            writer.write(data, sheet);
        }
        ParsedDocument doc = reader.parse(file.toFile());
        assertNotNull(doc);
        assertFalse(doc.segments().isEmpty());
        String allText = doc.segments().stream()
                .map(DocumentSegment::text).reduce("", String::concat);
        assertTrue(allText.contains("Alice"),
                "xlsx parse should reach Fesod and include Alice");
        assertTrue(allText.contains("Bob"),
                "xlsx parse should reach Fesod and include Bob");
        assertEquals("fesod", doc.metadata().get("format"));
        assertEquals(1, doc.metadata().get("sheetCount"));
    }

    @Test
    @DisplayName("parse(File) should produce segments from a PDF file (routed to Tika)")
    void parseLocalPdfFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("sample.pdf");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("This is PDF paragraph ").append(i).append(" of sample content. ");
        }
        writeTextPdf(file, sb.toString());
        ParsedDocument doc = reader.parse(file.toFile());
        assertNotNull(doc);
        assertFalse(doc.segments().isEmpty());
        String allText = doc.segments().stream()
                .map(DocumentSegment::text).reduce("", String::concat);
        assertTrue(allText.contains("This is PDF paragraph"),
                "PDF parse should reach Tika and extract text");
        assertTrue(doc.metadata().get("contentType") != null
                || doc.metadata().containsKey("format"),
                "docMeta should expose format information");
    }

    @Test
    @DisplayName("parse(String) auto-detects file path and routes to TextFastPathParser via Tika")
    void parseStringPath(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("readme.txt");
        Files.writeString(file, "Hello via String path!");
        ParsedDocument doc = reader.parse(file.toAbsolutePath().toString());
        assertNotNull(doc);
        assertFalse(doc.segments().isEmpty());
        assertTrue(doc.segments().get(0).text().contains("Hello"));
    }

    @Test
    @DisplayName("parse(InputStream, nameHint) should produce segments from in-memory content")
    void parseInputStream() throws Exception {
        String content = "Hello via InputStream!\n\nSecond in-memory paragraph.";
        ParsedDocument doc = reader.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "in-memory.txt");
        assertNotNull(doc);
        assertFalse(doc.segments().isEmpty());
        String allText = doc.segments().stream()
                .map(DocumentSegment::text).reduce("", String::concat);
        assertTrue(allText.contains("Hello via InputStream"));
        assertTrue(allText.contains("Second in-memory"));
    }

    @Test
    @DisplayName("parse(URL) should fetch via UrlFetcher three lines of defense and parse")
    void parseUrlWithLocalServer(@TempDir Path tempDir) throws Exception {
        Path pdfFile = tempDir.resolve("remote.pdf");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            sb.append("Remote PDF content line ").append(i).append(". ");
        }
        writeTextPdf(pdfFile, sb.toString());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/remote.pdf", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, -1);
            } else {
                byte[] body = Files.readAllBytes(pdfFile);
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            URL url = URI.create("http://127.0.0.1:" + port + "/remote.pdf").toURL();
            ParsedDocument doc = reader.parse(url);
            assertNotNull(doc);
            assertFalse(doc.segments().isEmpty());
            String allText = doc.segments().stream()
                    .map(DocumentSegment::text).reduce("", String::concat);
            assertTrue(allText.contains("Remote PDF content line"),
                    "URL fetch + Tika parse should yield extracted text");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("parseStream should handle multiple concurrent PDFs in parallel")
    void parseStream_concurrentMultiplePdfs(@TempDir Path tempDir) throws Exception {
        int N = 5;
        List<Path> pdfs = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Path pdf = tempDir.resolve("concurrent-" + i + ".pdf");
            writeTextPdf(pdf, "Concurrent PDF content " + i);
            pdfs.add(pdf);
        }

        List<ParseEvent> allEvents = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> futures = pdfs.stream()
                .map(pdf -> CompletableFuture.runAsync(() ->
                        reader.parseStream(
                                new ParserSource.FileSource(pdf.toFile()),
                                allEvents::add)))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long finishedCount = allEvents.stream()
                .filter(e -> e instanceof ParseEvent.Finished)
                .count();
        assertEquals(N, finishedCount,
                "expected N Finished events from N concurrent PDFs, got " + finishedCount);
        long failedCount = allEvents.stream()
                .filter(e -> e instanceof ParseEvent.Failed)
                .count();
        assertEquals(0, failedCount, "no PDF should fail to parse, got " + failedCount);
    }

    private static void writeTextPdf(Path file, String text) throws IOException {
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
    }
}
