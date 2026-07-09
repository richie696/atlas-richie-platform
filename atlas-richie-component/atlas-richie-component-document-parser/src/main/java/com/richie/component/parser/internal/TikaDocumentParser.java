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
package com.richie.component.parser.internal;

import com.richie.component.parser.DocumentParser;
import com.richie.component.parser.DocumentSegment;
import com.richie.component.parser.ImageSegment;
import com.richie.component.parser.ParseEvent;
import com.richie.component.parser.ParseListener;
import com.richie.component.parser.ParsedDocument;
import com.richie.component.parser.ParserContext;
import com.richie.component.parser.ParserSource;
import com.richie.component.parser.config.ParserProperties;
import com.richie.component.parser.exception.DocumentParseException;
import com.richie.component.parser.exception.ImageOnlyPdfException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final int DEFAULT_WRITE_LIMIT = -1; // unlimited

    private final ParserProperties properties;

    public TikaDocumentParser(ParserProperties properties) {
        this.properties = properties;
    }


    @Override
    public void parseStream(ParserSource source, ParserContext ctx, ParseListener listener) {
        try {
            byte[] bytes = readAllBytes(source);
            String nameHint = source.nameHint();

            List<DocumentSegment> collected = new ArrayList<>();
            int[] totalImages = {0};
            Metadata metadata = new Metadata();
            if (nameHint != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, nameHint);
            }

            BodyContentHandler handler = new BodyContentHandler(DEFAULT_WRITE_LIMIT);
            Parser parser = new AutoDetectParser();
            ParseContext parseContext = new ParseContext();

            Format format = FormatDetector.detectFormat(
                    new ByteArrayInputStream(bytes), nameHint);
            if (format != Format.PDF) {
                parseContext.set(EmbeddedDocumentExtractor.class,
                        new EmbeddedImageByteExtractor(listener, totalImages, nameHint));
            }

            String xhtml;
            try (TikaInputStream tikaStream = TikaInputStream.get(
                    new ByteArrayInputStream(bytes))) {
                parser.parse(tikaStream, handler, metadata, parseContext);
                xhtml = handler.toString();
            }

            // Strict mode: if image-only-detection is enabled, throw before
            // emitting any events so the caller fails fast on image-only PDFs.
            checkImageOnlyPdf(xhtml, source);

            emitXhtmlSegments(xhtml, collected, listener);
            emitXhtmlImages(xhtml, listener, totalImages);

            Map<String, Object> meta = new HashMap<>();
            meta.put("format", "tika");
            String title = metadata.get(TikaCoreProperties.TITLE);
            String author = metadata.get(TikaCoreProperties.CREATOR);
            if (title != null) meta.put("title", title);
            if (author != null) meta.put("author", author);
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            if (contentType != null) meta.put("contentType", contentType);
            ParsedDocument summary = new ParsedDocument(
                    title, author, collected, meta);
            listener.onEvent(new ParseEvent.Finished(
                    summary, collected.size(), totalImages[0]));
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
                                "TikaDocumentParser does not accept URL source directly. "
                                        + "UrlFetcher must download the URL into a stream first (Phase 5)."
                        );
            };
        } catch (IOException e) {
            throw new DocumentParseException(
                    "Failed to read bytes from " + source.nameHint(), e);
        }
    }

    private void emitXhtmlSegments(String xhtml, List<DocumentSegment> collected,
                                    ParseListener listener) {
        if (xhtml == null || xhtml.isEmpty()) {
            return;
        }
        Document jsoup = Jsoup.parse(xhtml);

        // Emit per <p> / <div> / <td> paragraphs
        Elements blocks = jsoup.select("p, div, td, h1, h2, h3, h4, h5, h6");
        if (!blocks.isEmpty()) {
            for (Element block : blocks) {
                String text = block.text().trim();
                if (text.isEmpty()) continue;
                DocumentSegment seg = new DocumentSegment(
                        text, null, "/" + jsoup.location(), Map.of("tag", block.tagName()));
                collected.add(seg);
                listener.onEvent(new ParseEvent.Streaming(seg));
            }
            return;
        }

        // Fallback: full text as one segment
        String allText = jsoup.text().trim();
        if (!allText.isEmpty()) {
            DocumentSegment seg = new DocumentSegment(
                    allText, null, "/" + jsoup.location(), Map.of());
            collected.add(seg);
            listener.onEvent(new ParseEvent.Streaming(seg));
        }
    }

    /**
     * 用 Jsoup 扫描 Tika XHTML 中的 {@code &lt;img&gt;} 标签,emit {@code ParseEvent.ImageStreaming}。
     * <p>
     * 注意: 简化方案不取实际图片字节 (data 字段填空 byte[]),只 emit src/alt/title 元数据。
     * 调用方需要图片字节可走 OCR 缓存 / 对象存储 / 外部 CDN 重新拉取。
     * <p>
     * 完整 EmbeddedResourceExtractor 方案需要 tika-parser-core 模块,
     * 与当前 3.3.1 BOM 解析的依赖有冲突,留待后续 phase 重新接入。
     */
    private void emitXhtmlImages(String xhtml, ParseListener listener, int[] totalImages) {
        if (xhtml == null || xhtml.isEmpty()) {
            return;
        }
        Document jsoup = Jsoup.parse(xhtml);
        Elements imgs = jsoup.select("img");
        for (Element img : imgs) {
            String src = img.attr("src");
            String alt = img.attr("alt");
            String title = img.attr("title");
            String mime = inferMimeFromSrc(src);
            String sectionPath = !src.isBlank() ? src : "/(unknown-img)";

            Map<String, Object> meta = new HashMap<>();
            meta.put("src", src);
            meta.put("alt", alt);
            meta.put("title", title);
            meta.put("mimeType", mime);

            ImageSegment imageSegment = new ImageSegment(
                    mime,
                    new byte[0],
                    !alt.isBlank() ? alt : title,
                    null,
                    null,
                    sectionPath,
                    meta
            );
            listener.onEvent(new ParseEvent.ImageStreaming(imageSegment));
            totalImages[0]++;
        }

        // Synthetic fallback: when Tika emits no <img> wrappers (common for image-only
        // PDFs whose embedded images are raw XObjects not HTML-wrapped), emit one
        // synthetic ImageStreaming so the caller knows the page is image-only and
        // can run OCR / VLM. Detection is intentionally permissive (any zero <img>
        // count triggers this) because false positives are harmless — the caller
        // simply ignores the synthetic event if the page is actually fine.
        if (imgs.isEmpty()) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("synthetic", true);
            meta.put("reason", "Tika XHTML had no <img> wrappers; may contain raw image XObjects");
            ImageSegment placeholder = new ImageSegment(
                    "image/unknown",
                    new byte[0],
                    "(image-only or embedded-raw-image page)",
                    null,
                    null,
                    "/(image-only-page)",
                    meta
            );
            listener.onEvent(new ParseEvent.ImageStreaming(placeholder));
            totalImages[0]++;
        }
    }

    private String inferMimeFromSrc(String src) {
        if (src == null) {
            return "image/unknown";
        }
        String lower = src.toLowerCase();
        if (lower.startsWith("data:image/png")) return "image/png";
        if (lower.startsWith("data:image/jpeg") || lower.startsWith("data:image/jpg")) return "image/jpeg";
        if (lower.startsWith("data:image/gif")) return "image/gif";
        if (lower.startsWith("data:image/bmp")) return "image/bmp";
        if (lower.startsWith("data:image/svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/unknown";
    }

    /**
     * 严格模式检查: 当 {@code image-only-detection.enabled=true} 时,
     * 触发 {@code ImageOnlyPdfException} 强制业务方 fail-fast。
     * <p>
     * 默认 {@code enabled=false}, 业务方拿到 emit 的 ImageStreaming event 自己处理 (OCR/VLM)。
     */
    private void checkImageOnlyPdf(String xhtml, ParserSource source) {
        var detection = properties.getPdf().getImageOnlyDetection();
        if (!detection.isEnabled()) return;
        String contentType = null;
        // contentType unknown here in stream mode; rely on heuristics
        Document jsoup = Jsoup.parse(xhtml == null ? "" : xhtml);
        int imageCount = jsoup.select("img").size();
        int textChars = jsoup.text().length();
        boolean imageRich = imageCount >= detection.getMinImageCount();
        boolean tooFewText = textChars < detection.getMinTextChars();
        boolean almostEmpty = textChars < 50;
        if ((tooFewText && imageRich) || almostEmpty) {
            throw new ImageOnlyPdfException(
                    imageCount, textChars, -1, source.nameHint());
        }
    }

    private static final class EmbeddedImageByteExtractor implements EmbeddedDocumentExtractor {

        private final ParseListener listener;
        private final int[] totalImages;
        private final String nameHint;
        private int index = 0;

        EmbeddedImageByteExtractor(ParseListener listener, int[] totalImages, String nameHint) {
            this.listener = listener;
            this.totalImages = totalImages;
            this.nameHint = nameHint != null ? nameHint : "doc";
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
                byte[] bytes;
                if (stream instanceof TikaInputStream tis) {
                    bytes = tis.readAllBytes();
                } else {
                    bytes = stream.readAllBytes();
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
