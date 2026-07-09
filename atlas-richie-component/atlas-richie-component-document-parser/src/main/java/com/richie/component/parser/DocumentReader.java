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
import com.richie.component.parser.exception.DocumentParseException;
import com.richie.component.parser.internal.Format;
import com.richie.component.parser.internal.FormatDetector;
import com.richie.component.parser.internal.ParserRouter;
import com.richie.component.parser.internal.UrlFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档解析入口 — 开发者接触的唯一 API。
 * <p>
 * 提供 4 个重载方法(File / InputStream / URL / 字符串路径自动识别),
 * 内部委托给对应的 {@link DocumentParser} 实现。
 * <p>
 * 用法:
 * <pre>{@code
 * DocumentReader reader = new DocumentReader(properties, router, urlFetcher);
 * ParsedDocument doc = reader.parse(new File("report.pdf"));
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public class DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(DocumentReader.class);

    private final ParserProperties properties;
    private final ParserRouter router;
    private final UrlFetcher urlFetcher;

    public DocumentReader(ParserProperties properties,
                          ParserRouter router,
                          UrlFetcher urlFetcher) {
        this.properties = properties;
        this.router = router;
        this.urlFetcher = urlFetcher;
    }

    /**
     * 解析本地文件。
     */
    public ParsedDocument parse(File file) {
        return parse(new ParserSource.FileSource(file));
    }

    /**
     * 解析输入流(调用方负责关闭流)。
     *
     * @param in       输入流
     * @param nameHint 文件名提示(用于扩展名嗅探,例如 {@code "report.docx"})
     */
    public ParsedDocument parse(InputStream in, String nameHint) {
        return parse(new ParserSource.StreamSource(in, nameHint));
    }

    /**
     * 解析 HTTPS URL(走 UrlFetcher 三道防线)。
     */
    public ParsedDocument parse(URL url) {
        return parse(new ParserSource.UrlSource(url, deriveUrlPolicy()));
    }

    /**
     * 解析字符串路径或 URL,自动识别:
     * <ul>
     *   <li>以 {@code http://} 或 {@code https://} 开头 → URL</li>
     *   <li>以 {@code file://} 开头 → 本地文件</li>
     *   <li>其他 → 本地文件路径</li>
     * </ul>
     */
    public ParsedDocument parse(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new IllegalArgumentException("pathOrUrl must not be blank");
        }
        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                return parse(URI.create(trimmed).toURL());
            } catch (IllegalArgumentException | java.net.MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL: " + trimmed, e);
            }
        }
        if (trimmed.startsWith("file://")) {
            try {
                return parse(new File(new URI(trimmed)));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid file URI: " + trimmed, e);
            }
        }
        return parse(new File(trimmed));
    }

    /**
     * 解析 {@code ParserSource} 任意形态 — 统一入口。
     * <p>
     * 流程: UrlSource -> UrlFetcher 下载 -> FormatDetector 嗅探 -> ParserRouter 分发。
     */
    public ParsedDocument parse(ParserSource source) {
        log.debug("[parser] dispatch start: {}", source.nameHint());
        try {
            InputStream stream;
            String nameHint;
            if (source instanceof ParserSource.UrlSource urlSource) {
                stream = urlFetcher.fetch(urlSource);
                nameHint = source.nameHint();
            } else if (source instanceof ParserSource.StreamSource streamSource) {
                stream = streamSource.in();
                nameHint = streamSource.nameHint();
            } else if (source instanceof ParserSource.FileSource fileSource) {
                try {
                    stream = new ByteArrayInputStream(
                            Files.readAllBytes(fileSource.file().toPath()));
                } catch (IOException e) {
                    throw new DocumentParseException(
                            "File not found: " + fileSource.file(), e);
                }
                nameHint = fileSource.nameHint();
            } else {
                throw new DocumentParseException(
                        "Unsupported ParserSource type: " + source.getClass().getName());
            }

            Format format = FormatDetector.detectFormat(stream, nameHint);
            log.debug("[parser] detected format: {} for {}", format, nameHint);
            DocumentParser parser = router.route(format);
            ParserSource streamSource = new ParserSource.StreamSource(stream, nameHint);
            List<DocumentSegment> collected = new ArrayList<>();
            ParsedDocument[] summaryHolder = new ParsedDocument[1];
            int[] totalImages = {0};
            Throwable[] failure = {null};
            parser.parseStream(streamSource, ParserContext.defaults(), event -> {
                switch (event) {
                    case ParseEvent.Streaming s -> collected.add(s.segment());
                    case ParseEvent.ImageStreaming ignored -> totalImages[0]++;
                    case ParseEvent.Finished f -> summaryHolder[0] = f.summary();
                    case ParseEvent.Failed err -> failure[0] = err.error();
                }
            });
            if (failure[0] instanceof DocumentParseException dpe) {
                throw dpe;
            }
            if (failure[0] != null) {
                throw new DocumentParseException("Parse failed", failure[0]);
            }
            if (summaryHolder[0] != null) {
                return summaryHolder[0];
            }
            return new ParsedDocument(null, null, collected, Map.of("totalImages", totalImages[0]));
        } catch (DocumentParseException e) {
            log.warn("[parser] parse failed for {}: {}", source.nameHint(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("[parser] unexpected error for {}", source.nameHint(), e);
            throw new DocumentParseException(
                    "Unexpected error parsing: " + source.nameHint(), e);
        }
    }

    /**
     * 解析 {@code ParserSource} 并指定上下文。
     */
    public ParsedDocument parse(ParserSource source, ParserContext ctx) {
        return parse(source);
    }

    /**
     * 流式解析入口 — 边读边 emit {@code ParseEvent},业务方订阅即可消费。
     * <p>
     * 与 {@link #parse} 不同的关键点:
     * <ul>
     *   <li>解析过程中每发现一段文本 → emit {@code ParseEvent.Streaming} (DocumentSegment)</li>
     *   <li>每发现一张图片 → emit {@code ParseEvent.ImageStreaming} (ImageSegment)</li>
     *   <li>解析完成 → emit {@code ParseEvent.Finished} (含汇总 ParsedDocument + 计数)</li>
     *   <li>解析失败 → emit {@code ParseEvent.Failed} (异常, 不 throw)</li>
     * </ul>
     * <p>
     * 适合场景: 外部系统拉取几十上百个 PDF, 边读边解析, 每段立即送 embedding / 入库。
     */
    public void parseStream(ParserSource source, ParseListener listener) {
        log.debug("[parser] stream dispatch start: {}", source.nameHint());
        try {
            InputStream stream;
            String nameHint;
            if (source instanceof ParserSource.UrlSource urlSource) {
                stream = urlFetcher.fetch(urlSource);
                nameHint = source.nameHint();
            } else if (source instanceof ParserSource.StreamSource streamSource) {
                stream = streamSource.in();
                nameHint = streamSource.nameHint();
            } else if (source instanceof ParserSource.FileSource fileSource) {
                try {
                    stream = new ByteArrayInputStream(
                            Files.readAllBytes(fileSource.file().toPath()));
                } catch (IOException e) {
                    throw new DocumentParseException(
                            "File not found: " + fileSource.file(), e);
                }
                nameHint = fileSource.nameHint();
            } else {
                throw new DocumentParseException(
                        "Unsupported ParserSource type: " + source.getClass().getName());
            }

            Format format = FormatDetector.detectFormat(stream, nameHint);
            log.debug("[parser] detected format: {} for {}", format, nameHint);
            DocumentParser parser = router.route(format);
            parser.parseStream(source, ParserContext.defaults(), listener);
        } catch (DocumentParseException dpe) {
            log.warn("[parser] stream failed for {}: {}", source.nameHint(), dpe.getMessage());
            listener.onEvent(new ParseEvent.Failed(dpe));
        } catch (RuntimeException e) {
            log.error("[parser] stream unexpected error for {}", source.nameHint(), e);
            listener.onEvent(new ParseEvent.Failed(
                    new DocumentParseException("Unexpected error parsing: " + source.nameHint(), e)));
        }
    }

    // ============ Internal ============

    private UrlFetchPolicy deriveUrlPolicy() {
        var url = properties.getUrl();
        return new UrlFetchPolicy(
                url.isAllowHttp(),
                url.isAllowPrivateIp(),
                url.isFollowRedirects(),
                url.getMaxBytes(),
                url.getConnectTimeout(),
                url.getReadTimeout(),
                List.of()
        );
    }
}
