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

import cn.richie696.component.parser.*;
import cn.richie696.component.parser.config.ParserProperties;
import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.exception.ImageOnlyPdfException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tika 文档解析器 — 流式 emit 文本 + 提取嵌入图片。
 * <p>
 * 处理 PDF / Word / PPT / ODF / RTF / HTML / XML 等 (除 Excel 外所有格式)。
 * <p>
 * <b>流式文本</b>: 解析 XHTML 后用 Jsoup 逐段 {@code <p>/<div>/<td>} 选区 emit {@code ParseEvent.Streaming}。
 * <p>
 * <b>图片提取</b>: Tika 解析时通过 {@code EmbeddedResourceExtractor} 拦截嵌入资源,
 * 过滤 image/* MIME 类型,emit {@code ParseEvent.ImageStreaming} (原样返回字节)。
 * <p>
 * <b>image-only PDF</b>: 默认不再抛 {@code ImageOnlyPdfException} (业务方可走 OCR / VLM 处理图片)。
 * 严格模式可通过 {@code platform.component.parser.pdf.image-only-detection.enabled=true} 开启。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class TikaDocumentParser implements DocumentParser {

    private final ParserProperties properties;

    public TikaDocumentParser(ParserProperties properties) {
        this.properties = properties;
    }


    @Override
    public void parseStream(ParserSource source, ParserContext ctx, ParseListener listener) {
        try {
            String nameHint = source.nameHint();

            int[] totalImages = {0};
            Metadata metadata = new Metadata();
            if (nameHint != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, nameHint);
            }

            Parser parser = new AutoDetectParser();
            ParseContext parseContext = new ParseContext();
            // Tika 3.3.1: PDFParser OCR 默认=AUTO,未配置 OCR 实现时
            // AbstractPDF2XHTML.doOCROnCurrentPage() 会因 ocrParser==null 抛 NPE。强制 NO_OCR 绕过。
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
            parseContext.set(PDFParserConfig.class, pdfConfig);
            parseContext.set(EmbeddedDocumentExtractor.class,
                    new EmbeddedImageByteExtractor(listener, totalImages, nameHint,
                            properties.getMaxEmbeddedImageBytes()));
            StreamingTextHandler handler = new StreamingTextHandler(listener, nameHint,
                    ctx.maxSegmentLength());
            try (InputStream in = openSource(source);
                 TikaInputStream tikaStream = TikaInputStream.get(in)) {
                parser.parse(tikaStream, handler, metadata, parseContext);
            }

            checkImageOnlyPdf(handler, totalImages[0], source);

            Map<String, Object> meta = new HashMap<>();
            meta.put("format", "tika");
            String title = metadata.get(TikaCoreProperties.TITLE);
            String author = metadata.get(TikaCoreProperties.CREATOR);
            if (title != null) meta.put("title", title);
            if (author != null) meta.put("author", author);
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            if (contentType != null) meta.put("contentType", contentType);
            listener.onEvent(new ParseEvent.Finished(
                    new DocumentSummary(title, author, meta), handler.segmentCount(), totalImages[0]));
        } catch (DocumentParseException dpe) {
            // Preserve original message from readAllBytes (e.g. "File not found",
            // "does not accept URL source directly") instead of wrapping it.
            listener.onEvent(new ParseEvent.Failed(dpe));
        } catch (IOException | TikaException | SAXException | RuntimeException e) {
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("Tika parse failed: " + source.nameHint(), e)));
        }
    }

    // ============ Internal ============

    private InputStream openSource(ParserSource source) {
        return switch (source) {
            case ParserSource.FileSource f -> {
                try {
                    yield java.nio.file.Files.newInputStream(f.file().toPath());
                } catch (NoSuchFileException e) {
                    throw new DocumentParseException("File not found: " + f.file(), e);
                } catch (IOException e) {
                    throw new DocumentParseException("Failed to open " + f.file(), e);
                }
            }
            case ParserSource.StreamSource s -> s.in();
            case ParserSource.UrlSource ignored -> throw new DocumentParseException(
                    "TikaDocumentParser does not accept URL source directly");
        };
    }

    /**
     * 严格模式检查: 当 {@code image-only-detection.enabled=true} 时,
     * 触发 {@code ImageOnlyPdfException} 强制业务方 fail-fast。
     * <p>
     * 默认 {@code enabled=false}, 业务方拿到 emit 的 ImageStreaming event 自己处理 (OCR/VLM)。
     */
    private void checkImageOnlyPdf(StreamingTextHandler handler, int imageCount, ParserSource source) {
        var detection = properties.getPdf().getImageOnlyDetection();
        if (!detection.isEnabled()) return;
        int textChars = handler.textCharacters();
        boolean imageRich = imageCount >= detection.getMinImageCount();
        boolean tooFewText = textChars < detection.getMinTextChars();
        boolean almostEmpty = textChars < 50;
        if ((tooFewText && imageRich) || almostEmpty) {
            throw new ImageOnlyPdfException(
                    imageCount, textChars, -1, source.nameHint());
        }
    }

    /**
     * 逐 SAX 块发出文本，最多只保留当前块，避免全文 DOM 与全文字符串。
     */
    private static final class StreamingTextHandler extends DefaultHandler {
        private static final Set<String> BLOCKS = Set.of("p", "div", "td", "th", "li",
                "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote");
        private static final int DEFAULT_FALLBACK_FLUSH_SIZE = 8 * 1024;

        private final ParseListener listener;
        private final String nameHint;
        private final int fallbackFlushSize;
        private final StringBuilder buffer = new StringBuilder();
        private String blockTag;
        private int blockDepth;
        private int segmentCount;
        private int textCharacters;

        private StreamingTextHandler(ParseListener listener, String nameHint, Integer maxSegmentLength) {
            this.listener = listener;
            this.nameHint = nameHint == null ? "document" : nameHint;
            this.fallbackFlushSize = maxSegmentLength == null
                    ? DEFAULT_FALLBACK_FLUSH_SIZE : Math.max(1, maxSegmentLength);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tag = normalize(localName, qName);
            if (blockDepth == 0 && BLOCKS.contains(tag)) {
                flush();
                blockTag = tag;
                blockDepth = 1;
            } else if (blockDepth > 0) {
                blockDepth++;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (length == 0) return;
            buffer.append(ch, start, length);
            textCharacters += length;
            if (buffer.length() >= fallbackFlushSize) {
                flush();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (blockDepth > 0 && --blockDepth == 0) {
                flush();
                blockTag = null;
            }
        }

        @Override
        public void endDocument() {
            flush();
        }

        int segmentCount() {
            return segmentCount;
        }

        int textCharacters() {
            return textCharacters;
        }

        private void flush() {
            String text = buffer.toString().trim();
            buffer.setLength(0);
            if (text.isEmpty()) return;
            Map<String, Object> meta = new HashMap<>();
            meta.put("format", "tika");
            if (blockTag != null) meta.put("tag", blockTag);
            listener.onEvent(new ParseEvent.Streaming(new DocumentSegment(text, null,
                    "/" + nameHint + "/Block[" + (segmentCount + 1) + "]", meta)));
            segmentCount++;
        }

        private static String normalize(String localName, String qName) {
            String name = localName == null || localName.isBlank() ? qName : localName;
            return name == null ? "" : name.toLowerCase();
        }
    }

    private static final class EmbeddedImageByteExtractor implements EmbeddedDocumentExtractor {

        private final ParseListener listener;
        private final int[] totalImages;
        private final String nameHint;
        private final long maxImageBytes;
        private int index = 0;

        EmbeddedImageByteExtractor(ParseListener listener, int[] totalImages, String nameHint, long maxImageBytes) {
            this.listener = listener;
            this.totalImages = totalImages;
            this.nameHint = nameHint != null ? nameHint : "doc";
            this.maxImageBytes = maxImageBytes;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            String mime = metadata.get(Metadata.CONTENT_TYPE);
            return mime != null && mime.toLowerCase().startsWith("image/");
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler,
                                  Metadata metadata, boolean outputHtml) {
            try {
                if (maxImageBytes <= 0 || maxImageBytes > Integer.MAX_VALUE - 1) {
                    throw new IllegalStateException("maxEmbeddedImageBytes 配置非法");
                }
                byte[] bytes = stream.readNBytes((int) maxImageBytes + 1);
                if (bytes.length > maxImageBytes) {
                    return;
                }
                if (bytes.length == 0) {
                    return;
                }
                String mime = metadata.get(Metadata.CONTENT_TYPE);
                String embeddedName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                int idx = index++;
                String sectionPath = "/embedded/" + nameHint + "/Image-" + idx;

                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "tika-embedded-extractor");
                meta.put("mimeType", mime != null ? mime : "image/unknown");
                meta.put("size", bytes.length);

                ImageSegment imageSegment = new ImageSegment(
                        mime != null ? mime : "image/unknown",
                        bytes,
                        embeddedName != null ? embeddedName : "Image-" + idx,
                        null,
                        null,
                        sectionPath,
                        meta
                );
                listener.onEvent(new ParseEvent.ImageStreaming(imageSegment));
                totalImages[0]++;
            } catch (IOException | RuntimeException e) {
                // Swallow extractor failures — never break the outer Tika parsing pipeline.
            }
        }
    }
}
