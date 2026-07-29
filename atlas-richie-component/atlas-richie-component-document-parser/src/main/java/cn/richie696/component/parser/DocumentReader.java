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
import cn.richie696.component.parser.internal.*;
import cn.richie696.component.parser.model.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.AutoCloseable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
 * {@link ParsedSection} / {@link cn.richie696.component.parser.model.ParsedImage}。
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

    /**
     * 同步解析本地文件, 返回 {@link ReadResult}; 失败抛 {@link DocumentParseException}。
     */
    public ReadResult read(File file) {
        log.info("read() entry: file={}", file);
        return read(file, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制同步解析本地文件。
     */
    public ReadResult read(File file, ParserContext context) {
        Objects.requireNonNull(file, "file must not be null");
        return runSync(sourceFromFile(file), file.getName(), context);
    }

    /**
     * 同步解析输入流。
     *
     * @param in       输入流
     * @param nameHint 文件名提示(扩展名嗅探, 例如 {@code "report.docx"})
     */
    public ReadResult read(InputStream in, String nameHint) {
        log.info("read() entry: stream, nameHint={}", nameHint);
        return read(in, nameHint, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制同步解析输入流。
     */
    public ReadResult read(InputStream in, String nameHint, ParserContext context) {
        Objects.requireNonNull(in, "in must not be null");
        return runSync(new ParserSource.StreamSource(in, nameHint), nameHint, context);
    }

    /**
     * 同步解析 URL, 走 {@link UrlFetcher} 三道防线。
     */
    public ReadResult read(URL url) {
        log.info("read() entry: url={}", url);
        return read(url, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制同步解析 URL。
     */
    public ReadResult read(URL url, ParserContext context) {
        Objects.requireNonNull(url, "url must not be null");
        return runSync(
                new ParserSource.UrlSource(url, deriveUrlPolicy()),
                url.toString(), context);
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
        readStreaming(file, listener, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制流式解析本地文件。
     */
    public void readStreaming(File file, ReadListener listener, ParserContext context) {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        runStreamForListener(sourceFromFile(file), file.getName(), listener, context);
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
        readStreaming(in, nameHint, listener, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制流式解析输入流。
     */
    public void readStreaming(InputStream in, String nameHint, ReadListener listener, ParserContext context) {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        runStreamForListener(new ParserSource.StreamSource(in, nameHint), nameHint, listener, context);
    }

    /**
     * 以 JDK Reactive Streams 协议发布文件解析事件。
     *
     * <p>每个订阅独占一个解析线程；仅当订阅者通过 {@link Flow.Subscription#request(long)} 提供需求额度时
     * 才会继续向其交付 Section/Image/Finished 事件。因此慢速下游会反压到解析线程，而不会形成无界内存队列。</p>
     */
    public Flow.Publisher<ReadEvent> readPublisher(File file) {
        Objects.requireNonNull(file, "file must not be null");
        return readPublisher(file, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制发布文件解析事件。
     */
    public Flow.Publisher<ReadEvent> readPublisher(File file, ParserContext context) {
        Objects.requireNonNull(file, "file must not be null");
        return new ReadEventPublisher(listener -> readStreaming(file, listener, context));
    }

    /**
     * 以 JDK Reactive Streams 协议发布输入流解析事件。取消订阅时会关闭输入流。
     */
    public Flow.Publisher<ReadEvent> readPublisher(InputStream in, String nameHint) {
        Objects.requireNonNull(in, "in must not be null");
        return readPublisher(in, nameHint, defaultContext());
    }

    /**
     * 使用调用方指定的资源限制发布输入流解析事件。取消订阅时会关闭输入流。
     */
    public Flow.Publisher<ReadEvent> readPublisher(InputStream in, String nameHint, ParserContext context) {
        Objects.requireNonNull(in, "in must not be null");
        return new ReadEventPublisher(listener -> readStreaming(in, nameHint, listener, context), in);
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

    private ReadResult runSync(ParserSource source, String displayName, ParserContext context) {
        SyncAccumulator sink = new SyncAccumulator();
        runStream(source, displayName, ReadEventRecorder.adaptForSync(sink), context);
        return sink.toResult();
    }

    /**
     * 对公开 listener API 统一把基础设施失败映射为终止事件；同步 API 则保留异常语义。
     */
    private void runStreamForListener(ParserSource source, String displayName, ReadListener listener,
                                      ParserContext context) {
        try {
            runStream(source, displayName, listener, context);
        } catch (DocumentParseException error) {
            listener.onEvent(new ReadEvent.Failed(error));
        }
    }

    private void runStream(ParserSource source, String displayName, ReadListener outListener,
                           ParserContext context) {
        ParserContext effectiveContext = context == null ? defaultContext() : context;
        ParseListener internalListener = getParseListener(displayName, outListener, effectiveContext);
        try {
            ParserSource expanded = expandIfUrl(source);
            ParserSource.StreamSource streamSource = asBufferedStream(expanded);
            String nameHint = streamSource.nameHint();
            log.info("dispatch start: source={}, expanded={}",
                    displayName, expanded.getClass().getSimpleName());
            try (InputStream input = streamSource.in()) {
                Format format = FormatDetector.detectFormat(input, nameHint);
                log.info("format detected: format={} for {}", format, nameHint);
                DocumentParser parser = router.route(format);
                log.info("parser routed: {} for format={}", parser.getClass().getSimpleName(), format);
                parser.parseStream(new ParserSource.StreamSource(input, nameHint), effectiveContext, internalListener);
            }
            log.info("parse complete for {}", displayName);
        } catch (IOException ioe) {
            log.warn("parse stream close failed for {}: {}", displayName, ioe.getMessage());
            throw new DocumentParseException("Parse stream close failed for " + displayName, ioe);
        } catch (DocumentParseException dpe) {
            log.warn("parse failed for {}: {}", displayName, dpe.getMessage());
            throw dpe;
        } catch (RuntimeException re) {
            log.error("unexpected parse error for {}", displayName, re);
            throw new DocumentParseException(
                    "Parse failed for " + displayName + ": " + re.getMessage(), re);
        }
    }

    private @NonNull ParseListener getParseListener(String displayName, ReadListener outListener,
                                                    ParserContext context) {
        Instant deadline = Instant.now().plus(context.timeout());
        return event -> {
            if (Instant.now().isAfter(deadline)) {
                throw new DocumentParseException("解析超时: " + displayName);
            }
            if (event instanceof ParseEvent.Streaming streaming && context.maxSegmentLength() != null
                    && streaming.segment().text().length() > context.maxSegmentLength()) {
                throw new DocumentParseException("解析段超过上限: " + context.maxSegmentLength());
            }
            ReadEvent mapped = mapInternalEvent(event, displayName);
            if (mapped instanceof ReadEvent.Section s) {
                outListener.onEvent(s);
            } else if (mapped instanceof ReadEvent.Image i) {
                outListener.onEvent(i);
            } else if (event instanceof ParseEvent.Finished f) {
                DocumentSummary summary = f.summary();
                outListener.onEvent(new ReadEvent.Finished(
                        new ReadSummary(summary.title(), summary.author(), summary.metadata()),
                        f.totalSegments(), f.totalImages()));
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
            try {
                return new ParserSource.StreamSource(urlFetcher.fetch(u), u.nameHint());
            } catch (RuntimeException e) {
                log.warn("URL fetch failed: {}", u.url(), e);
                throw new DocumentParseException("URL fetch failed: " + u.url(), e);
            }
        }
        return source;
    }

    private ParserSource.StreamSource asBufferedStream(ParserSource source) {
        try {
            return switch (source) {
                case ParserSource.FileSource(File file) -> new ParserSource.StreamSource(
                        new BufferedInputStream(java.nio.file.Files.newInputStream(file.toPath())), source.nameHint());
                case ParserSource.StreamSource(InputStream in, String nameHint) -> new ParserSource.StreamSource(
                        in instanceof BufferedInputStream ? in : new BufferedInputStream(in), nameHint);
                case ParserSource.UrlSource ignored -> throw new DocumentParseException(
                        "URL source must be expanded before stream dispatch");
            };
        } catch (IOException e) {
            throw new DocumentParseException("Read failed: " + source.nameHint(), e);
        }
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

    private ParserContext defaultContext() {
        return new ParserContext(properties.getParseTimeout(), properties.getMaxSegmentCharacters(), Map.of());
    }

    // ============ Accumulators ============

    /**
     * 同步模式累加器 — 把 Section / Image 收进 ReadResult; Failed 直接抛出。
     */
    private static final class SyncAccumulator implements ReadListener {
        private final List<ParsedSection> sections = new ArrayList<>();
        private final List<cn.richie696.component.parser.model.ParsedImage> images = new ArrayList<>();
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
                this.title = f.summary().title();
                this.author = f.summary().author();
                this.metadata.putAll(f.summary().metadata());
            } else if (event instanceof ReadEvent.Failed(DocumentParseException error)) {
                throw error;
            }
        }

        ReadResult toResult() {
            metadata.putIfAbsent("format", "unknown");
            return new ReadResult(title, author, sections, images, metadata);
        }
    }

    /**
     * 把内部 ParseListener 桥接为对外 ReadListener (sync 路径使用, 简化累加)。
     */
    private static final class ReadEventRecorder {
        private ReadEventRecorder() {
        }

        static ReadListener adaptForSync(SyncAccumulator sink) {
            return sink;
        }
    }

    @FunctionalInterface
    private interface StreamingStarter {
        void start(ReadListener listener);
    }

    /**
     * 单订阅、按 demand 阻塞解析线程的 Flow Publisher。
     */
    private static final class ReadEventPublisher implements Flow.Publisher<ReadEvent> {
        private final StreamingStarter starter;
        private final AutoCloseable closeOnCancel;
        private final AtomicBoolean subscribed = new AtomicBoolean();

        private ReadEventPublisher(StreamingStarter starter) {
            this(starter, null);
        }

        private ReadEventPublisher(StreamingStarter starter, AutoCloseable closeOnCancel) {
            this.starter = starter;
            this.closeOnCancel = closeOnCancel;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ReadEvent> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            if (!subscribed.compareAndSet(false, true)) {
                subscriber.onSubscribe(new EmptySubscription());
                subscriber.onError(new IllegalStateException("每个 ReadEvent Publisher 仅支持一个订阅者"));
                return;
            }
            Subscription subscription = new Subscription(subscriber, starter, closeOnCancel);
            subscriber.onSubscribe(subscription);
        }

        private static final class Subscription implements Flow.Subscription {
            private final Flow.Subscriber<? super ReadEvent> subscriber;
            private final StreamingStarter starter;
            private final AutoCloseable closeOnCancel;
            private final Object monitor = new Object();
            private long requested;
            private boolean started;
            private volatile boolean cancelled;
            private Thread worker;

            private Subscription(Flow.Subscriber<? super ReadEvent> subscriber, StreamingStarter starter,
                                 AutoCloseable closeOnCancel) {
                this.subscriber = subscriber;
                this.starter = starter;
                this.closeOnCancel = closeOnCancel;
            }

            @Override
            public void request(long n) {
                if (n <= 0) {
                    cancel();
                    subscriber.onError(new IllegalArgumentException("request 数量必须大于 0"));
                    return;
                }
                synchronized (monitor) {
                    requested = requested > Long.MAX_VALUE - n ? Long.MAX_VALUE : requested + n;
                    if (!started) {
                        started = true;
                        worker = new Thread(this::run, "atlas-document-parser-stream");
                        worker.setDaemon(true);
                        worker.start();
                    }
                    monitor.notifyAll();
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
                closeQuietly();
                Thread thread = worker;
                if (thread != null) {
                    thread.interrupt();
                }
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }

            private void run() {
                try {
                    starter.start(this::emit);
                    if (!cancelled) {
                        subscriber.onComplete();
                    }
                } catch (Throwable error) {
                    if (!cancelled) {
                        subscriber.onError(error);
                    }
                } finally {
                    closeQuietly();
                }
            }

            private void emit(ReadEvent event) {
                synchronized (monitor) {
                    while (!cancelled && requested == 0) {
                        try {
                            monitor.wait();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            cancel();
                            return;
                        }
                    }
                    if (cancelled) {
                        return;
                    }
                    if (requested != Long.MAX_VALUE) {
                        requested--;
                    }
                }
                subscriber.onNext(event);
            }

            private void closeQuietly() {
                if (closeOnCancel == null) {
                    return;
                }
                try {
                    closeOnCancel.close();
                } catch (Exception ignored) {
                    // cancellation cleanup is best effort
                }
            }
        }

        private static final class EmptySubscription implements Flow.Subscription {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        }
    }
}
