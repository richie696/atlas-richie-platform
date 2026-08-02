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

import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.exception.FormatNotSupportedException;
import cn.richie696.component.parser.exception.ImageOnlyPdfException;
import cn.richie696.component.parser.internal.ReadEventAdapter;
import cn.richie696.component.parser.model.ParsedImage;
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadResult;
import cn.richie696.component.parser.model.ReadSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 纯模型类型 / 值对象综合测试 — 构造器兜底、equals/hashCode/toString 契约。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class ModelTypesTest {

    // ==================== ParsedImage ====================

    @Test
    @DisplayName("ParsedImage 构造器: 空值兜底 + getter")
    void parsedImage_defaults() {
        ParsedImage image = new ParsedImage(null, null, null, null, null);
        assertEquals("image/unknown", image.format());
        assertEquals(0, image.size());
        assertEquals(0, image.data().length);
        assertThat(image.meta()).isEmpty();
    }

    @Test
    @DisplayName("ParsedImage: equals/hashCode/toString")
    void parsedImage_contract() {
        byte[] data = {1, 2, 3};
        ParsedImage a = new ParsedImage("png", data, "img.png", "/a/Img[0]", Map.of("k", "v"));
        ParsedImage b = new ParsedImage("png", data, "img.png", "/a/Img[0]", Map.of("k", "v"));
        ParsedImage c = new ParsedImage("jpg", data, "img.png", "/a/Img[0]", Map.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertThat(a.toString()).contains("format='png'").contains("size=3");
        assertNotEquals(a, "not-an-image");
    }

    // ==================== ParsedSection ====================

    @Test
    @DisplayName("ParsedSection: null text 抛异常, meta 兜底")
    void parsedSection_validation() {
        assertThrows(IllegalArgumentException.class, () -> new ParsedSection(null, "/p", null));
        ParsedSection section = new ParsedSection("hello", "/p", null);
        assertEquals("hello", section.text());
        assertThat(section.meta()).isEmpty();
    }

    @Test
    @DisplayName("ParsedSection: equals/toString 截断")
    void parsedSection_contract() {
        String longText = "x".repeat(100);
        ParsedSection a = new ParsedSection(longText, "/p", null);
        ParsedSection b = new ParsedSection(longText, "/p", Map.of());
        ParsedSection other = new ParsedSection("short", "/p", null);
        assertEquals(a, b);
        assertNotEquals(a, other);
        String s = a.toString();
        assertThat(s).contains("...").contains("/p");
        assertEquals(60 + 3 + "ParsedSection{path='/p', text='".length() + "', meta={}}".length(), s.length());
    }

    // ==================== ReadResult ====================

    @Test
    @DisplayName("ReadResult: 空值兜底 + 计数")
    void readResult_defaults() {
        ReadResult result = new ReadResult(null, null, null, null, null);
        assertThat(result.sections()).isEmpty();
        assertThat(result.images()).isEmpty();
        assertThat(result.metadata()).isEmpty();
        assertEquals(0, result.sectionCount());
        assertEquals(0, result.imageCount());
    }

    @Test
    @DisplayName("ReadResult: equals/hashCode/toString")
    void readResult_contract() {
        ParsedSection section = new ParsedSection("text", "/p", null);
        ParsedImage image = new ParsedImage("png", new byte[]{1}, "i", "/i", null);
        ReadResult a = new ReadResult("t", "au", List.of(section), List.of(image), Map.of("m", 1));
        ReadResult b = new ReadResult("t", "au", List.of(section), List.of(image), Map.of("m", 1));
        ReadResult c = new ReadResult("t", "au", List.of(section), List.of(image), Map.of("m", 2));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertThat(a.toString()).contains("sections=1").contains("images=1");
        assertNotEquals(a, null);
    }

    // ==================== ReadEvent ====================

    @Test
    @DisplayName("ReadEvent: 4 种 record 可构造")
    void readEvent_records() {
        ParsedSection section = new ParsedSection("t", "/p", null);
        ParsedImage image = new ParsedImage("png", new byte[]{1}, "i", "/i", null);
        DocumentParseException error = new DocumentParseException("boom");
        ReadEvent.Section e1 = new ReadEvent.Section(section, "a.txt");
        ReadEvent.Image e2 = new ReadEvent.Image(image, "a.txt");
        ReadEvent.Finished e3 = new ReadEvent.Finished(new ReadSummary("t", "au", Map.of()), 1, 2);
        ReadEvent.Failed e4 = new ReadEvent.Failed(error);
        assertThat(e1.fileName()).isEqualTo("a.txt");
        assertThat(e2.image()).isSameAs(image);
        assertThat(e3.totalSections()).isEqualTo(1);
        assertThat(e3.summary().title()).isEqualTo("t");
        assertThat(e4.error()).isSameAs(error);
    }

    @Test
    @DisplayName("ReadSummary: metadata null 兜底")
    void readSummary_defaults() {
        assertThat(new ReadSummary(null, null, null).metadata()).isEmpty();
    }

    // ==================== ImageSegment ====================

    @Test
    @DisplayName("ImageSegment: 构造器 + data/meta 空值兜底")
    void imageSegment_defaults() {
        ImageSegment seg = new ImageSegment("image/unknown", null, null, 1, 2, "/s", null);
        assertEquals("image/unknown", seg.format());
        assertEquals(0, seg.data().length);
        assertThat(seg.meta()).isEmpty();
        assertThat(seg.sectionPath()).isEqualTo("/s");
    }

    @Test
    @DisplayName("ImageSegment: 完整构造 + accessor")
    void imageSegment_full() {
        ImageSegment seg = new ImageSegment("png", new byte[]{1, 2}, "img.png", 3, 4, "/s", Map.of("a", 1));
        assertEquals(2, seg.data().length);
        assertEquals(3, seg.pageNumber());
        assertEquals(4, seg.slideNumber());
    }

    // ==================== DocumentSegment / ParsedDocument / DocumentSummary ====================

    @Test
    @DisplayName("DocumentSegment: null text/meta 兜底")
    void documentSegment_defaults() {
        DocumentSegment seg = new DocumentSegment(null, null, null, null);
        assertEquals("", seg.text());
        assertThat(seg.meta()).isEmpty();
    }

    @Test
    @DisplayName("ParsedDocument: null segments/metadata 兜底")
    void parsedDocument_defaults() {
        ParsedDocument doc = new ParsedDocument(null, null, null, null);
        assertThat(doc.segments()).isEmpty();
        assertThat(doc.metadata()).isEmpty();
    }

    @Test
    @DisplayName("DocumentSummary: metadata null 兜底")
    void documentSummary_defaults() {
        assertThat(new DocumentSummary(null, null, null).metadata()).isEmpty();
    }

    // ==================== ParserContext ====================

    @Test
    @DisplayName("ParserContext: null timeout 兜底 60s, attributes 拷贝")
    void parserContext_defaults() {
        ParserContext ctx = new ParserContext(null, null, Map.of("k", "v"));
        assertEquals(Duration.ofSeconds(60), ctx.timeout());
        assertThat(ctx.attributes()).containsEntry("k", "v");
        assertEquals(Duration.ofSeconds(60), ParserContext.defaults().timeout());
    }

    @Test
    @DisplayName("ParserContext: 非法 timeout / maxSegmentLength 抛异常")
    void parserContext_validation() {
        assertThrows(IllegalArgumentException.class, () -> new ParserContext(Duration.ZERO, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ParserContext(Duration.ofSeconds(-1), null, null));
        assertThrows(IllegalArgumentException.class, () -> new ParserContext(null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new ParserContext(null, -5, null));
    }

    // ==================== ParserSource ====================

    @Test
    @DisplayName("FileSource: null 抛异常, nameHint = 文件名")
    void fileSource_contract() {
        assertThrows(IllegalArgumentException.class, () -> new ParserSource.FileSource(null));
        assertThat(new ParserSource.FileSource(new File("a.txt")).nameHint()).isEqualTo("a.txt");
    }

    @Test
    @DisplayName("StreamSource: null 抛异常, blank nameHint 兜底为 stream")
    void streamSource_contract() {
        assertThrows(IllegalArgumentException.class, () -> new ParserSource.StreamSource(null, "x"));
        assertThat(new ParserSource.StreamSource(new ByteArrayInputStream(new byte[0]), "  ").nameHint())
                .isEqualTo("stream");
    }

    @Test
    @DisplayName("UrlSource: null 抛异常, policy 兜底 defaults")
    void urlSource_contract() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ParserSource.UrlSource(null, null));
        URL url = new URL("https://example.com/a.pdf");
        ParserSource.UrlSource source = new ParserSource.UrlSource(url, null);
        assertThat(source.policy()).isNotNull();
        assertThat(source.nameHint()).isEqualTo(url.toString());
    }

    // ==================== UrlFetchPolicy ====================

    @Test
    @DisplayName("UrlFetchPolicy: 空值兜底 + defaults")
    void urlFetchPolicy_defaults() {
        UrlFetchPolicy policy = new UrlFetchPolicy(true, false, false, 0, null, null, null);
        assertEquals(200L * 1024 * 1024, policy.maxBytes());
        assertEquals(Duration.ofSeconds(5), policy.connectTimeout());
        assertEquals(Duration.ofSeconds(60), policy.readTimeout());
        assertThat(policy.allowlist()).isEmpty();
        assertThat(UrlFetchPolicy.defaults().allowHttp()).isFalse();
    }

    @Test
    @DisplayName("CidrBlock: blank cidr 抛异常, contains 恒 false")
    void cidrBlock_contract() throws UnknownHostException {
        assertThrows(IllegalArgumentException.class, () -> new UrlFetchPolicy.CidrBlock(null));
        assertThrows(IllegalArgumentException.class, () -> new UrlFetchPolicy.CidrBlock(" "));
        UrlFetchPolicy.CidrBlock block = new UrlFetchPolicy.CidrBlock("10.0.0.0/8");
        assertThat(block.contains(InetAddress.getByName("10.1.2.3"))).isFalse();
        assertThat(block.cidr()).isEqualTo("10.0.0.0/8");
    }

    // ==================== ParseEvent ====================

    @Test
    @DisplayName("ParseEvent: 4 种 record + sourceName 解析")
    void parseEvent_sourceName() {
        DocumentSegment seg = new DocumentSegment("text", 1, "/a.txt/P[1]", null);
        ParseEvent.Streaming streaming = new ParseEvent.Streaming(seg);
        assertEquals("/a.txt/P[1]", streaming.sourceName());

        ImageSegment image = new ImageSegment("png", new byte[]{1}, "i", null, null, "/a.txt/Img[0]", null);
        ParseEvent.ImageStreaming imageStreaming = new ParseEvent.ImageStreaming(image);
        assertEquals("/a.txt/Img[0]", imageStreaming.sourceName());

        ParseEvent.Finished finished = new ParseEvent.Finished(
                new DocumentSummary("t", "au", Map.of("source", "file:///x.pdf")), 3, 2);
        assertEquals("file:///x.pdf", finished.sourceName());
        assertEquals(3, finished.totalSegments());

        ParseEvent.Failed failed = new ParseEvent.Failed(new DocumentParseException("err-msg"));
        assertEquals("err-msg", failed.sourceName());
    }

    @Test
    @DisplayName("ParseEvent.Finished: metadata 无 source 时 sourceName 兜底 unknown")
    void parseEvent_finishedFallback() {
        ParseEvent.Finished finished = new ParseEvent.Finished(
                new DocumentSummary("t", "au", Map.of()), 0, 0);
        assertEquals("unknown", finished.sourceName());
    }

    // ==================== ParseListener default 方法 ====================

    @Test
    @DisplayName("ParseListener: default 方法 no-op 可调用")
    void parseListener_defaults() {
        ParseListener listener = event -> {
        };
        listener.onStreaming(new DocumentSegment("t", null, "/p", null), "s");
        listener.onImage(new ImageSegment("png", new byte[]{1}, "i", null, null, "/i", null), "s");
        listener.onFinished(new DocumentSummary("t", "au", null), 1, 0, "s");
        listener.onError(new DocumentParseException("boom"), "s");
    }

    // ==================== ReadEventAdapter ====================

    @Test
    @DisplayName("ReadEventAdapter: Streaming/Image/Failed 适配")
    void readEventAdapter() {
        ReadEvent.Section section = ReadEventAdapter.toSection(
                new ParseEvent.Streaming(new DocumentSegment("text", 1, "/p", Map.of("k", "v"))), "a.txt");
        assertEquals("text", section.section().text());
        assertEquals("a.txt", section.fileName());
        assertThat(section.section().meta()).containsEntry("k", "v");

        ReadEvent.Image image = ReadEventAdapter.toImage(
                new ParseEvent.ImageStreaming(new ImageSegment("png", new byte[]{1}, "i", null, null, "/i", null)),
                "a.txt");
        assertEquals("png", image.image().format());
        assertEquals(1, image.image().size());

        DocumentParseException error = new DocumentParseException("boom");
        ReadEvent.Failed failed = ReadEventAdapter.toFailed(new ParseEvent.Failed(error));
        assertEquals(error, failed.error());
    }

    // ==================== 异常类 ====================

    @Test
    @DisplayName("FormatNotSupportedException: 字段 + cause 构造")
    void formatNotSupportedException() {
        FormatNotSupportedException ex = new FormatNotSupportedException("pdf", "msg");
        assertEquals("pdf", ex.getDetectedFormat());
        assertEquals("msg", ex.getMessage());
        RuntimeException cause = new RuntimeException("cause");
        FormatNotSupportedException withCause = new FormatNotSupportedException("x", "msg2", cause);
        assertEquals(cause, withCause.getCause());
    }

    @Test
    @DisplayName("ImageOnlyPdfException: 字段 + 消息")
    void imageOnlyPdfException() {
        ImageOnlyPdfException ex = new ImageOnlyPdfException(5, 10, 100L, "scan.pdf");
        assertEquals(5, ex.getImageCount());
        assertEquals(10, ex.getTextLength());
        assertEquals(100L, ex.getFileSize());
        assertThat(ex.getMessage()).contains("scan.pdf").contains("5");
    }
}
