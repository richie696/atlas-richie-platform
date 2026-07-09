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
package com.richie.component.parser;

import com.richie.component.parser.config.ParserProperties;
import com.richie.component.parser.exception.DocumentParseException;
import com.richie.component.parser.internal.Format;
import com.richie.component.parser.internal.FormatDetector;
import com.richie.component.parser.internal.ParserRouter;
import com.richie.component.parser.internal.ReadEventAdapter;
import com.richie.component.parser.internal.UrlFetcher;
import com.richie.component.parser.model.ParsedSection;
import com.richie.component.parser.model.ReadEvent;
import com.richie.component.parser.model.ReadListener;
import com.richie.component.parser.model.ReadResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

/**
 * 文档解析门面 — 业务方接触的唯一公开 API。
 * <p>
 * 提供 2 类入口:
 * <ul>
 *   <li><b>批式</b> {@code read(...)} — 一次性同步解析, 返回 {@link ReadResult}</li>
 *   <li><b>流式</b> {@code readStreaming(..., ReadListener)} — 边读边 emit {@link ReadEvent}</li>
 * </ul>
 * 入参只接受高层 Java 概念: {@link File} / {@link InputStream}+nameHint / {@link URL} /
 * {@link String}(自动识别 file / http / https / file:// URI)。
 * 内部 SPI 类型 ({@link ParserSource} / {@link ParseListener} / {@link ParseEvent} / {@link ParsedDocument})
 * 均不暴露给业务方 — 业务方只持有 model 包的 {@link ReadResult} / {@link ReadEvent} /
 * {@link ParsedSection} / {@link com.richie.component.parser.model.ParsedImage}。
 *
 * @author richie696
 * @version 2.0
 * @since 2026-07-09
 */
@Slf4j
public class DocumentReader {

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

    // ============ Batch sync reads ============

    /** 同步解析本地文件, 返回 {@link ReadResult}; 失败抛 {@link DocumentParseException}。 */
    public ReadResult read(File file) {
        log.info("read() entry: file={}", file);
        return runSync(sourceFromFile(file), file.getName());
    }

    /**
     * 同步解析输入流。
     *
     * @param in       输入流
     * @param nameHint 文件名提示(扩展名嗅探, 例如 {@code "report.docx"})
     */
    public ReadResult read(InputStream in, String nameHint) {
        log.info("read() entry: stream, nameHint={}", nameHint);
        return runSync(new ParserSource.StreamSource(in, nameHint), nameHint);
    }

    /** 同步解析 URL, 走 {@link UrlFetcher} 三道防线。 */
    public ReadResult read(URL url) {
        log.info("read() entry: url={}", url);
        return runSync(
                new ParserSource.UrlSource(url, deriveUrlPolicy()),
                url.toString());
    }

    /**
     * 同步解析字符串路径或 URL, 自动识别:
     * <ul>
     *   <li>{@code http://} / {@code https://} 前缀 → URL</li>
     *   <li>{@code file://} 前缀 → 本地文件 URI</li>
     *   <li>其他 → 本地文件路径</li>
     * </ul>
     */
    public ReadResult read(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new IllegalArgumentException("pathOrUrl must not be blank");
        }
        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                return read(URI.create(trimmed).toURL());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid URL: " + trimmed, e);
            }
        }
        if (trimmed.startsWith("file://")) {
            try {
                return read(new File(new URI(trimmed)));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid file URI: " + trimmed, e);
            }
        }
        return read(new File(trimmed));
    }

    // ============ Streaming reads ============

    /**
     * 流式解析本地文件 — 边读边 emit {@link ReadEvent} 至 {@link ReadListener#onEvent}。
     * 失败以 {@link ReadEvent.Failed} 事件方式上报, 不抛异常; 调用方可在 listener 中处理。
     */
    public void readStreaming(File file, ReadListener listener) {
        log.info("readStreaming() entry: file={}", file);
        runStream(sourceFromFile(file), file.getName(), listener);
    }

    /**
     * 流式解析输入流。
     *
     * @param in       输入流
     * @param nameHint 文件名提示
     * @param listener 事件订阅
     */
    public void readStreaming(InputStream in, String nameHint, ReadListener listener) {
        log.info("readStreaming() entry: stream, nameHint={}", nameHint);
        runStream(new ParserSource.StreamSource(in, nameHint), nameHint, listener);
    }

    /**
     * 批量流式解析多个本地文件 — 单 listener 顺序消费,每个文件的 Section / Image 事件携带
     * {@code fileName},Finished / Failed 事件作为分界信号。listener 无需维护每文件累加器,
     * 可直接 {@code Map<String, Consumer<ReadEvent>>} 路由。
     * <p>
     * 失败处理: 单个文件抛 {@link DocumentParseException} 时,先 emit {@link ReadEvent.Failed},
     * 再继续下一个文件(批任务内不中断)。
     *
     * @param files    待解析文件列表 (顺序处理)
     * @param listener 跨文件共享事件订阅
     */
    public void readStreamingAll(Collection<File> files, ReadListener listener) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (File file : files) {
            try {
                readStreaming(file, listener);
            } catch (DocumentParseException dpe) {
                listener.onEvent(ReadEventAdapter.toFailed(new ParseEvent.Failed(dpe)));
            } catch (RuntimeException re) {
                DocumentParseException dpe = new DocumentParseException(
                        "Batch parse failed for " + file.getName() + ": " + re.getMessage(), re);
                listener.onEvent(ReadEventAdapter.toFailed(new ParseEvent.Failed(dpe)));
            }
        }
    }

    // ============ Internal ============

    private ParserSource.FileSource sourceFromFile(File file) {
        if (!file.exists()) {
            throw new DocumentParseException("File not found: " + file);
        }
        return new ParserSource.FileSource(file);
    }

    private ReadResult runSync(ParserSource source, String displayName) {
        SyncAccumulator sink = new SyncAccumulator();
        runStream(source, displayName, ReadEventRecorder.adaptForSync(sink));
        return sink.toResult();
    }

    private void runStream(ParserSource source, String displayName, ReadListener outListener) {
        ParseListener internalListener = getParseListener(displayName, outListener);
        try {
            ParserSource expanded = expandIfUrl(source);
            byte[] bytes = readAllBytes(expanded);
            String nameHint = expanded.nameHint();
            log.info("dispatch start: source={}, bytes={}, expanded={}",
                    displayName, bytes.length, expanded.getClass().getSimpleName());
            ByteArrayInputStream repeatable = new ByteArrayInputStream(bytes);
            Format format = FormatDetector.detectFormat(repeatable, nameHint);
            log.info("format detected: format={} for {}", format, nameHint);
            DocumentParser parser = router.route(format);
            log.info("parser routed: {} for format={}", parser.getClass().getSimpleName(), format);
            parser.parseStream(
                    new ParserSource.StreamSource(repeatable, nameHint),
                    ParserContext.defaults(),
                    internalListener);
            log.info("parse complete for {}", displayName);
        } catch (DocumentParseException dpe) {
            log.warn("parse failed for {}: {}", displayName, dpe.getMessage());
            throw dpe;
        } catch (RuntimeException re) {
            log.error("unexpected parse error for {}", displayName, re);
            throw new DocumentParseException(
                    "Parse failed for " + displayName + ": " + re.getMessage(), re);
        }
    }

    private @NonNull ParseListener getParseListener(String displayName, ReadListener outListener) {
        StreamingAccumulator sink = new StreamingAccumulator();
        return event -> {
            ReadEvent mapped = mapInternalEvent(event, displayName);
            if (mapped instanceof ReadEvent.Section s) {
                sink.sections.add(s.section());
                outListener.onEvent(s);
            } else if (mapped instanceof ReadEvent.Image i) {
                sink.images.add(i.image());
                outListener.onEvent(i);
            } else if (event instanceof ParseEvent.Finished f) {
                ReadResult result = sink.toResult(f.summary());
                outListener.onEvent(new ReadEvent.Finished(result, sink.sections.size(), sink.images.size()));
            } else if (event instanceof ParseEvent.Failed err) {
                outListener.onEvent(ReadEventAdapter.toFailed(err));
            }
        };
    }

    private ReadEvent mapInternalEvent(ParseEvent event, String fileName) {
        if (event instanceof ParseEvent.Streaming s) {
            return ReadEventAdapter.toSection(s, fileName);
        }
        if (event instanceof ParseEvent.ImageStreaming img) {
            return ReadEventAdapter.toImage(img, fileName);
        }
        if (event instanceof ParseEvent.Failed err) {
            return ReadEventAdapter.toFailed(err);
        }
        return null;
    }

    private ParserSource expandIfUrl(ParserSource source) {
        if (source instanceof ParserSource.UrlSource u) {
            log.info("URL fetch start: {}", u.url());
            try (InputStream fetched = urlFetcher.fetch(u)) {
                byte[] bytes = fetched.readAllBytes();
                log.info("URL fetch OK: {} bytes from {}", bytes.length, u.url());
                return new ParserSource.StreamSource(
                        new ByteArrayInputStream(bytes), u.nameHint());
            } catch (IOException e) {
                log.warn("URL fetch failed: {}", u.url(), e);
                throw new DocumentParseException("URL fetch failed: " + u.url(), e);
            }
        }
        return source;
    }

    private byte[] readAllBytes(ParserSource source) {
        if (source instanceof ParserSource.FileSource(File file)) {
            try {
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                throw new DocumentParseException("Read failed: " + file, e);
            }
        }
        if (source instanceof ParserSource.StreamSource(InputStream in, String nameHint)) {
            try {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new DocumentParseException("Read failed: " + nameHint, e);
            }
        }
        if (source instanceof ParserSource.UrlSource) {
            throw new DocumentParseException("URL source should be expanded before readAllBytes()");
        }
        throw new DocumentParseException("Unsupported ParserSource: " + source.getClass().getName());
    }

    private UrlFetchPolicy deriveUrlPolicy() {
        var url = properties.getUrl();
        return new UrlFetchPolicy(
                url.isAllowHttp(),
                url.isAllowPrivateIp(),
                url.isFollowRedirects(),
                url.getMaxBytes(),
                url.getConnectTimeout(),
                url.getReadTimeout(),
                List.of());
    }

    // ============ Accumulators ============

    /** 同步模式累加器 — 把 Section / Image 收进 ReadResult; Failed 直接抛出。 */
    private static final class SyncAccumulator implements ReadListener {
        private final List<ParsedSection> sections = new ArrayList<>();
        private final List<com.richie.component.parser.model.ParsedImage> images = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private String title;
        private String author;

        @Override
        public void onEvent(ReadEvent event) {
            if (event instanceof ReadEvent.Section s) {
                sections.add(s.section());
            } else if (event instanceof ReadEvent.Image i) {
                images.add(i.image());
            } else if (event instanceof ReadEvent.Finished f) {
                this.title = f.result().title();
                this.author = f.result().author();
                this.metadata.putAll(f.result().metadata());
            } else if (event instanceof ReadEvent.Failed(DocumentParseException error)) {
                throw error;
            }
        }

        ReadResult toResult() {
            metadata.putIfAbsent("format", "unknown");
            return new ReadResult(title, author, sections, images, metadata);
        }
    }

    /** 流式模式累加器 — 仅记录 Section / Image 用于 Finished 汇总。 */
    private static final class StreamingAccumulator {
        private final List<ParsedSection> sections = new ArrayList<>();
        private final List<com.richie.component.parser.model.ParsedImage> images = new ArrayList<>();

        ReadResult toResult(ParsedDocument summary) {
            Map<String, Object> meta = summary != null && summary.metadata() != null
                    ? new HashMap<>(summary.metadata())
                    : new HashMap<>();
            meta.putIfAbsent("format", "unknown");
            String title = summary != null ? summary.title() : null;
            String author = summary != null ? summary.author() : null;
            return new ReadResult(title, author, sections, images, meta);
        }
    }

    /** 把内部 ParseListener 桥接为对外 ReadListener (sync 路径使用, 简化累加)。 */
    private static final class ReadEventRecorder {
        private ReadEventRecorder() {
        }

        static ReadListener adaptForSync(SyncAccumulator sink) {
            return sink;
        }
    }
}
