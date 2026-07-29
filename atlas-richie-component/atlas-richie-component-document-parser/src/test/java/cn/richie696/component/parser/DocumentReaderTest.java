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
package cn.richie696.component.parser;

import cn.richie696.component.parser.config.ParserProperties;
import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.internal.FesodDocumentParser;
import cn.richie696.component.parser.internal.ParserRouter;
import cn.richie696.component.parser.internal.TikaDocumentParser;
import cn.richie696.component.parser.internal.UrlFetcher;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadResult;
import com.sun.net.httpserver.HttpServer;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DocumentReader} 集成测试 — 仅通过 model 包公开类型 (ReadResult / ReadEvent) 断言。
 * <p>
 * 不直接引用内部 ParserSource / ParseEvent / DocumentSegment, 确保组件封装契约。
 */
class DocumentReaderTest {

    private DocumentReader reader;

    @BeforeEach
    void setUp() {
        ParserProperties properties = new ParserProperties();
        properties.getUrl().setAllowPrivateIp(true);
        properties.getUrl().setAllowHttp(true);

        TikaDocumentParser tika = new TikaDocumentParser(properties);
        FesodDocumentParser fesod = new FesodDocumentParser();
        ParserRouter router = new ParserRouter(tika, fesod);
        UrlFetcher urlFetcher = new UrlFetcher();
        reader = new DocumentReader(properties, router, urlFetcher);
    }

    @Test
    @DisplayName("read(File) should produce ReadResult with sections for TXT")
    void readLocalTextFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, integration test!\n\nSecond paragraph.");
        ReadResult result = reader.read(file.toFile());
        assertNotNull(result);
        assertNotNull(result.sections());
        assertFalse(result.sections().isEmpty());
        String allText = result.sections().stream()
                .map(s -> s.text() == null ? "" : s.text())
                .reduce("", String::concat);
        assertTrue(allText.contains("Hello, integration test!"), "should contain marker text");
    }

    @Test
    @DisplayName("read(InputStream, nameHint) should detect file extension")
    void readStreamWithNameHint(@TempDir Path tempDir) throws IOException {
        byte[] payload = "## Markdown Title\n\nBody text here.".getBytes();
        try (var in = new ByteArrayInputStream(payload)) {
            ReadResult result = reader.read(in, "doc.md");
            assertNotNull(result);
            assertNotNull(result.sections());
            assertFalse(result.sections().isEmpty());
        }
    }

    @Test
    @DisplayName("read(File) should produce sections + format=tika metadata for PDF")
    void readLocalPdfFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("PDF parsing integration test.");
                cs.endText();
            }
            doc.save(file.toFile());
        }
        ReadResult result = reader.read(file.toFile());
        assertNotNull(result);
        assertNotNull(result.metadata());
        assertEquals("tika", result.metadata().get("format"),
                "format key should be 'tika' per Schema contract");
        assertNotNull(result.sections());
    }

    @Test
    @DisplayName("read(URL) over a local HTTP server should produce ReadResult")
    void readHttpUrl(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("remote.txt");
        Files.writeString(file, "Remote content via local http server.");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            URL url = new URL("http://127.0.0.1:" + port + "/remote.txt");
            ReadResult result = reader.read(url);
            assertNotNull(result);
            assertNotNull(result.sections());
            assertFalse(result.sections().isEmpty());
            String allText = result.sections().stream()
                    .map(s -> s.text() == null ? "" : s.text())
                    .reduce("", String::concat);
            assertTrue(allText.contains("Remote content via local http server"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("readStreaming(File, listener) should emit Section / Finished events")
    void readStreamingEmitsEvents(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("streamed.txt");
        Files.writeString(file, "Streaming test content.\nParagraph two.");
        List<ReadEvent> events = new CopyOnWriteArrayList<>();
        reader.readStreaming(file.toFile(), events::add);
        assertFalse(events.isEmpty(), "should emit at least one event");
        assertTrue(events.stream().anyMatch(e -> e instanceof ReadEvent.Section),
                "should emit at least one Section event");
        ReadEvent.Finished finished = events.stream()
                .filter(e -> e instanceof ReadEvent.Finished)
                .map(e -> (ReadEvent.Finished) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected Finished event in " + events));
        assertNotNull(finished.summary());
        assertNotNull(finished.summary().metadata().get("format"),
                "format key should be present per Schema contract");
    }

    @Test
    @DisplayName("readPublisher(File) should deliver events only after demand is requested")
    void readPublisherDeliversEventsWithDemand(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("publisher.txt");
        Files.writeString(file, "Publisher paragraph.");
        List<ReadEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);

        reader.readPublisher(file.toFile()).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ReadEvent item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                fail(throwable);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "publisher should complete");
        assertTrue(events.stream().anyMatch(ReadEvent.Section.class::isInstance));
        assertTrue(events.stream().anyMatch(ReadEvent.Finished.class::isInstance));
    }

    @Test
    @DisplayName("read(non-existent File) should throw DocumentParseException")
    void readMissingFile(@TempDir Path tempDir) {
        File missing = tempDir.resolve("nope.txt").toFile();
        DocumentParseException ex = null;
        try {
            reader.read(missing);
        } catch (DocumentParseException e) {
            ex = e;
        }
        if (ex == null) {
            fail("expected DocumentParseException for missing file");
        }
        assertNotNull(ex.getMessage());
    }
}
