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
package cn.richie696.component.ocr.parser;

import cn.richie696.component.ocr.model.MimeType;
import cn.richie696.component.ocr.model.OcrImage;
import cn.richie696.component.ocr.model.OcrOptions;
import cn.richie696.component.ocr.model.OcrResult;
import cn.richie696.component.ocr.parser.config.OcrParserAdapterProperties;
import cn.richie696.component.ocr.provider.OcrProvider;
import cn.richie696.component.parser.exception.DocumentParseException;
import cn.richie696.component.parser.model.ParsedImage;
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadSummary;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;

/**
 * {@link OcrDocumentEnrichmentAdapter} 的默认实现。
 *
 * <p>适配器严格按上游背压逐个处理事件：一张图片完成 OCR 并向下游发出其派生事件后，
 * 才请求下一条解析事件。因此不会为大文档建立无界图片队列，也不创建隐式线程池。</p>
 */
public final class DefaultOcrDocumentEnrichmentAdapter implements OcrDocumentEnrichmentAdapter {

    private static final String OCR_PREFIX = "ocr.";

    private final OcrProvider provider;
    private final OcrParserAdapterProperties properties;

    public DefaultOcrDocumentEnrichmentAdapter(OcrProvider provider,
                                               OcrParserAdapterProperties properties) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (properties.getMaxImageBytes() <= 0) {
            throw new IllegalArgumentException("maxImageBytes must be greater than zero");
        }
        if (properties.getMinConfidence() < 0.0f || properties.getMinConfidence() > 1.0f) {
            throw new IllegalArgumentException("minConfidence must be between 0.0 and 1.0");
        }
    }

    @Override
    public Flow.Publisher<ReadEvent> enrich(Flow.Publisher<ReadEvent> source) {
        Objects.requireNonNull(source, "source");
        return downstream -> source.subscribe(new EnrichingSubscriber(downstream));
    }

    private final class EnrichingSubscriber implements Flow.Subscriber<ReadEvent>, Flow.Subscription {

        private final Flow.Subscriber<? super ReadEvent> downstream;
        private final ArrayDeque<ReadEvent> pendingEvents = new ArrayDeque<>();

        private Flow.Subscription upstream;
        private long requested;
        private boolean upstreamRequestOutstanding;
        private boolean upstreamCompleted;
        private boolean cancelled;
        private boolean terminalSignalled;
        private int addedSections;
        private int processedImages;
        private int skippedImages;
        private int failedImages;
        private int emittedImages;

        private EnrichingSubscriber(Flow.Subscriber<? super ReadEvent> downstream) {
            this.downstream = Objects.requireNonNull(downstream, "downstream");
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (upstream != null) {
                subscription.cancel();
                return;
            }
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(ReadEvent event) {
            if (cancelled || upstreamCompleted) {
                return;
            }
            upstreamRequestOutstanding = false;
            if (event instanceof ReadEvent.Image image) {
                enrichImage(image);
            } else if (event instanceof ReadEvent.Finished finished) {
                pendingEvents.add(enrichFinished(finished));
            } else if (event instanceof ReadEvent.Failed failed) {
                pendingEvents.add(failed);
                upstreamCompleted = true;
                upstream.cancel();
            } else {
                pendingEvents.add(event);
            }
            drain();
        }

        @Override
        public void onError(Throwable throwable) {
            if (terminalSignalled || cancelled) {
                return;
            }
            terminalSignalled = true;
            pendingEvents.clear();
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (cancelled || terminalSignalled) {
                return;
            }
            upstreamCompleted = true;
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                if (!terminalSignalled) {
                    terminalSignalled = true;
                    downstream.onError(new IllegalArgumentException("request amount must be greater than zero"));
                }
                return;
            }
            requested = addCap(requested, n);
            drain();
        }

        @Override
        public void cancel() {
            cancelled = true;
            pendingEvents.clear();
            if (upstream != null) {
                upstream.cancel();
            }
        }

        private void enrichImage(ReadEvent.Image imageEvent) {
            ParsedImage image = imageEvent.image();
            String skipReason = skipReason(image);
            if (skipReason != null) {
                skippedImages++;
                enqueueImage(imageEvent, "SKIPPED", skipReason, null);
                return;
            }

            MimeType mimeType = mimeTypeOf(image.format()).orElseThrow();
            processedImages++;
            try {
                OcrResult result = provider.recognize(
                        new OcrImage.Bytes(image.data(), mimeType), buildOptions());
                String text = result.highConfidenceText(properties.getMinConfidence()).trim();
                if (text.isEmpty() && result.overallConfidence() >= properties.getMinConfidence()) {
                    text = result.text().trim();
                }
                if (text.isEmpty()) {
                    skippedImages++;
                    enqueueImage(imageEvent, "LOW_CONFIDENCE", "no text passed the confidence threshold", result);
                    return;
                }
                enqueueImage(imageEvent, "SUCCESS", null, result);
                pendingEvents.add(new ReadEvent.Section(new ParsedSection(text, ocrSectionPath(image),
                        ocrSectionMetadata(image, result)), imageEvent.fileName()));
                addedSections++;
            } catch (RuntimeException exception) {
                failedImages++;
                if (properties.getFailureMode() == OcrFailureMode.FAIL_DOCUMENT) {
                    upstreamCompleted = true;
                    upstream.cancel();
                    pendingEvents.clear();
                    pendingEvents.add(new ReadEvent.Failed(new DocumentParseException(
                            "OCR enrichment failed for image " + imageName(image), exception)));
                    return;
                }
                enqueueImage(imageEvent, "FAILED", exception.getClass().getSimpleName(), null);
            }
        }

        private void enqueueImage(ReadEvent.Image imageEvent, String status, String reason, OcrResult result) {
            if (!properties.isEmitOriginalImage()) {
                return;
            }
            ParsedImage image = imageEvent.image();
            Map<String, Object> metadata = new HashMap<>(image.meta());
            metadata.put(OCR_PREFIX + "status", status);
            metadata.put(OCR_PREFIX + "provider", provider.getClass().getName());
            if (reason != null) {
                metadata.put(OCR_PREFIX + "reason", reason);
            }
            if (result != null) {
                metadata.put(OCR_PREFIX + "overallConfidence", result.overallConfidence());
                metadata.put(OCR_PREFIX + "latencyMs", result.latencyMs());
                metadata.put(OCR_PREFIX + "blockCount", result.blocks().size());
            }
            pendingEvents.add(new ReadEvent.Image(new ParsedImage(image.format(), image.data(), image.name(),
                    image.sectionPath(), metadata), imageEvent.fileName()));
            emittedImages++;
        }

        private ReadEvent.Finished enrichFinished(ReadEvent.Finished finished) {
            Map<String, Object> metadata = new HashMap<>(finished.summary().metadata());
            metadata.put(OCR_PREFIX + "enrichment", "enabled");
            metadata.put(OCR_PREFIX + "provider", provider.getClass().getName());
            metadata.put(OCR_PREFIX + "processedImages", processedImages);
            metadata.put(OCR_PREFIX + "skippedImages", skippedImages);
            metadata.put(OCR_PREFIX + "failedImages", failedImages);
            metadata.put(OCR_PREFIX + "sectionsAdded", addedSections);
            metadata.put(OCR_PREFIX + "sourceImages", finished.totalImages());
            ReadSummary summary = new ReadSummary(finished.summary().title(), finished.summary().author(), metadata);
            return new ReadEvent.Finished(summary, finished.totalSections() + addedSections, emittedImages);
        }

        private String skipReason(ParsedImage image) {
            if (image.size() > properties.getMaxImageBytes()) {
                return "image exceeds maxImageBytes";
            }
            if (mimeTypeOf(image.format()).isEmpty()) {
                return "unsupported image MIME type: " + image.format();
            }
            if (!isConfiguredMimeType(image.format())) {
                return "image MIME type is disabled by supportedMimeTypes";
            }
            return null;
        }

        private boolean isConfiguredMimeType(String format) {
            if (properties.getSupportedMimeTypes() == null) {
                return false;
            }
            return properties.getSupportedMimeTypes().stream()
                    .filter(Objects::nonNull)
                    .map(DefaultOcrDocumentEnrichmentAdapter::normalizeMime)
                    .anyMatch(normalizeMime(format)::equals);
        }

        private OcrOptions buildOptions() {
            return OcrOptions.builder()
                    .detectOrientation(properties.isDetectOrientation())
                    .tableRecognition(properties.isTableRecognition())
                    .outputBoundingBoxes(true)
                    .confidenceThreshold(properties.getMinConfidence())
                    .build();
        }

        private Map<String, Object> ocrSectionMetadata(ParsedImage image, OcrResult result) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("contentSource", "OCR");
            metadata.put(OCR_PREFIX + "provider", provider.getClass().getName());
            metadata.put(OCR_PREFIX + "overallConfidence", result.overallConfidence());
            metadata.put(OCR_PREFIX + "latencyMs", result.latencyMs());
            metadata.put(OCR_PREFIX + "blockCount", result.blocks().size());
            metadata.put(OCR_PREFIX + "pageMetadata", result.pageMetadata());
            if (image.name() != null) {
                metadata.put("image.name", image.name());
            }
            metadata.put("image.mimeType", image.format());
            if (image.sectionPath() != null) {
                metadata.put("image.sectionPath", image.sectionPath());
            }
            return metadata;
        }

        private String ocrSectionPath(ParsedImage image) {
            String sourcePath = image.sectionPath();
            return (sourcePath == null || sourcePath.isBlank() ? "/image" : sourcePath) + "/ocr";
        }

        private String imageName(ParsedImage image) {
            return image.name() == null || image.name().isBlank() ? image.sectionPath() : image.name();
        }

        private void drain() {
            if (cancelled || terminalSignalled) {
                return;
            }
            while (requested > 0 && !pendingEvents.isEmpty()) {
                requested--;
                downstream.onNext(pendingEvents.removeFirst());
                if (cancelled || terminalSignalled) {
                    return;
                }
            }
            if (upstreamCompleted && pendingEvents.isEmpty()) {
                terminalSignalled = true;
                downstream.onComplete();
                return;
            }
            if (requested > 0 && !upstreamRequestOutstanding && upstream != null) {
                upstreamRequestOutstanding = true;
                upstream.request(1);
            }
        }
    }

    private static Optional<MimeType> mimeTypeOf(String format) {
        String normalized = normalizeMime(format);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        for (MimeType value : MimeType.values()) {
            if (value.contentType().equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static String normalizeMime(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long addCap(long current, long increment) {
        long result = current + increment;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
