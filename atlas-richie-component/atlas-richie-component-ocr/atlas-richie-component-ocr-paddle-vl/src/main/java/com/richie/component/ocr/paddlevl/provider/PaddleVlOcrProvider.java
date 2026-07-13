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
package com.richie.component.ocr.paddlevl.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.model.Languages;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.paddlevl.config.PaddleVlOcrProperties;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;
import com.richie.component.ocr.paddlevl.protocol.VlRequest;
import com.richie.component.ocr.paddlevl.protocol.VlResponse;
import com.richie.component.ocr.paddlevl.protocol.VlSubmitEnvelope;
import com.richie.component.ocr.paddlevl.protocol.VlSubmitPayload;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PaddleOCR-VL Provider 实现
 *
 * <p>Sidecar HTTP REST 协议（gRPC 切换是中台后续工作）:
 * <ul>
 *   <li>{@code POST {grpc-endpoint}/submit} 请求体 {@code {"image": "<base64>", "options": {...}}} → 返回 {@code {"task_id": "..."}}</li>
 *   <li>{@code GET {grpc-endpoint}/tasks/{task_id}} → 返回 {@code {"state": "PENDING|RUNNING|SUCCEEDED|FAILED", "result": {...}, "error_code": "..."}}</li>
 * </ul>
 *
     * <p><b>同步</b>: VLM 推理慢, {@link #recognize} 仅对
     * 小图（{@value #MAX_SYNC_BYTES} 字节内）开放，大图直接抛 {@link OcrException.ImageTooLargeForSync}。
 *
 * <p><b>错误码映射</b>:
 * <ul>
 *   <li>{@code VLM_OOM} → {@link OcrException.VlmOutOfMemory}</li>
 *   <li>HTTP 5xx / 连接失败 → {@link OcrException.SidecarUnavailable}</li>
 *   <li>轮询超时 → {@link OcrException.VlmTimeout}</li>
 *   <li>FAILED 状态 / 业务 error_code → {@link OcrException.Unrecognized}</li>
 * </ul>
 *
 * <p><b>热更新友好 (L2)</b>: 所有 vendor 配置属性（{@code grpc-endpoint} / {@code gpu-pool} /
 * {@code timeout-ms}）都不在构造期固化，改为在每次调用时按需通过 {@code liveXxx()} 方法读取
 * {@link #props}。此种模式下：
 * <ul>
 *   <li>Provider 不会"闭锁"配置 —— 业务侧通过 Spring Cloud {@code @RefreshScope} /
 *       Nacos Config Listener 替换 {@link PaddleVlOcrProperties} Bean 实例后，下次调用即生效</li>
 *   <li>无运行时分支、无 reflection、无 thread-local</li>
 *   <li>{@code submit(...)} 在 sync 路径上调用，{@code liveXxx()} 只在调用瞬间读 {@link #props}，不做任何
 *       跨调用的缓存，hot-update 不会与 in-flight 任务产生半生不熟的中间值</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public class PaddleVlOcrProvider extends AbstractOcrProvider<VlRequest, VlResponse> {

    /** 同步识别字节阈值: VLM 推理慢, 超过该值直接抛 ImageTooLargeForSync */
    private static final int MAX_SYNC_BYTES = 1024 * 1024;
    private static final long DEFAULT_TIMEOUT_MS = 120_000L;
    private static final String DEFAULT_ENDPOINT = "http://localhost:50051";
    private static final int DEFAULT_GPU_POOL = 1;
    private static final long POLL_INTERVAL_MS = 2_000L;

    private static String blankTo(String value) {
        return value == null || value.isBlank() ? PaddleVlOcrProvider.DEFAULT_ENDPOINT : value;
    }

    /** 调用 PaddleOCR-VL sidecar 的共享 HTTP 客户端（不是 vendor 配置，不走 props）。 */
    private final HttpClient httpClient;

    /** PaddleOCR-VL vendor 配置 Properties —— 每次调用 lazy 读取。 */
    private final PaddleVlOcrProperties props;

    /**
     * 构造 PaddleOCR-VL Provider；供应商配置由 typed {@link PaddleVlOcrProperties} 通过构造器注入，
     * 无中间 JsonNode 表示，配置由 typed PaddleVlOcrProperties POJO 直接注入。
     *
     * <p>仅保存 {@code props} 引用并 fast-fail 校验 {@code gpu-pool}（必须 {@code >= 1}，与物理 GPU
     * 卡数对齐）；其他配置（{@code grpc-endpoint} / {@code timeout-ms}）在每次调用时通过
     * {@code liveXxx()} 实时读取。
     *
     * @param props typed 供应商配置（provider-name / grpc-endpoint / gpu-pool / timeout-ms）
     * @param httpClient 共享的 HTTP 客户端，用于调用 PaddleOCR-VL sidecar 的 submit / 轮询接口
     * @throws OcrException.ConfigMissing 当 {@code gpu-pool} 配置无效（{@code < 1}）时抛出
     */
    public PaddleVlOcrProvider(PaddleVlOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.props = props;

        // Fail-fast: liveGpuPool() 在 helper 内部已经对 ≤0 做了 fallback，所以这里实际上是
        // 防御性检查 —— 当业务侧通过外部手段把 props.getGpuPool() 置为 0 时也能尽早抛错。
        if (liveGpuPool() < 1) {
            throw new OcrException.ConfigMissing("paddle-vl", "gpu-pool");
        }
    }

    // --- live configuration accessors: 每次调用时实时读 props ---
    // 业务侧可以通过 Spring @RefreshScope / Nacos Config Listener 等替换 props Bean 引用实现热更新；
    // helper 只在调用瞬间读字段，不做任何缓存，因此 in-flight 任务与配置更新不会互相干扰。

    private long liveTimeoutMs() {
        return props.getTimeoutMs() > 0 ? props.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
    }

    private int liveGpuPool() {
        return props.getGpuPool() > 0 ? props.getGpuPool() : DEFAULT_GPU_POOL;
    }

    private String liveGrpcEndpoint() {
        return blankTo(props.getGrpcEndpoint());
    }

    /**
     * 将统一的 {@link OcrImage} 物化为字节数组，并拼装 PaddleOCR-VL 提交所需的 {@link VlRequest}。
     *
     * <p>仅支持 {@link OcrImage.Bytes} 与 {@link OcrImage.Stream}；{@link OcrImage.Url}
     * 因 sidecar 不支持而直接拒绝。
     *
     * @param image 待识别图片，支持 {@code Bytes} 与 {@code Stream}
     * @param options 调用选项，用于提取语言与是否启用表格识别
     * @return 包含图片字节、语言码与表格识别开关的 VL 请求对象
     * @throws OcrException.Unrecognized 当传入的 {@link OcrImage} 类型为 {@code Url} 时抛出
     * @throws OcrException.ProviderUnavailable 当读取 {@link OcrImage.Stream} 输入流发生 {@link IOException} 时抛出
     */
    @Override
    protected VlRequest toProviderRequest(OcrImage image, OcrOptions options) {
        byte[] data;
        if (image instanceof OcrImage.Bytes b) {
            data = b.data();
        } else if (image instanceof OcrImage.Stream s) {
            try (InputStream in = s.input()) {
                data = in.readAllBytes();
            } catch (IOException e) {
                throw new OcrException.ProviderUnavailable("paddle-vl", null, e);
            }
        } else {
            throw new OcrException.Unrecognized("paddle-vl",
                    "PaddleOCR-VL only supports local Bytes/Stream images, not URL");
        }
        return new VlRequest(data, mapLanguage(options.languages()), options.tableRecognition());
    }

    /**
     * 同步识别入口；对图片字节长度做前置校验，超过 {@link #MAX_SYNC_BYTES}（1 MB）时直接抛
     * {@link OcrException.ImageTooLargeForSync}。
     *
     * @param image 待识别图片，仅支持 {@link OcrImage.Bytes} 与 {@link OcrImage.Stream}
     * @param options 调用选项
     * @return 统一的 OCR 识别结果
     * @throws OcrException.ImageTooLargeForSync 当图片字节数大于 1 MB 时抛出
     * @throws OcrException.ProviderUnavailable 当读取 {@link OcrImage.Stream} 输入流发生 {@link IOException} 时抛出
     */
    @Override
    public OcrResult recognize(OcrImage image, OcrOptions options) {
        if (image instanceof OcrImage.Bytes b) {
            byte[] data = b.data();
            if (data.length > MAX_SYNC_BYTES) {
                throw new OcrException.ImageTooLargeForSync("paddle-vl", data.length, MAX_SYNC_BYTES);
            }
            return super.recognize(image, options);
        }
        if (image instanceof OcrImage.Stream s) {
            byte[] data;
            try (InputStream in = s.input()) {
                data = in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new OcrException.ProviderUnavailable("paddle-vl", null, e);
            }
            if (data.length > MAX_SYNC_BYTES) {
                throw new OcrException.ImageTooLargeForSync("paddle-vl", data.length, MAX_SYNC_BYTES);
            }
            return super.recognize(new OcrImage.Bytes(data, bMimeType(image)), options);
        }
        return super.recognize(image, options);
    }

    private static com.richie.component.ocr.model.MimeType bMimeType(OcrImage source) {
        if (source instanceof OcrImage.Stream s) {
            return s.mime();
        }
        return com.richie.component.ocr.model.MimeType.PNG;
    }

    /**
     * 调用 PaddleOCR-VL sidecar 提交图片并同步轮询直至完成，返回最终响应。
     *
     * @param request 已构造好的 VL 请求（图片字节、语言、表格识别开关）
     * @return 包含任务 ID、终态、结果 JSON 与耗时的 VL 响应
     * @throws OcrException.SidecarUnavailable 当 HTTP 5xx、连接失败或 IO 异常时抛出
     * @throws OcrException.VlmTimeout 当轮询超过当前 {@code liveTimeoutMs()} 仍未完成时抛出
     * @throws OcrException.VlmOutOfMemory 当任务失败且 error_code 为 {@code VLM_OOM} 时抛出
     * @throws OcrException.Unrecognized 当任务终态为 {@code FAILED} 且非 OOM 业务错误时抛出
     * @throws OcrException.ProviderUnavailable 当轮询被中断时抛出，并恢复线程中断状态
     */
    @Override
    protected VlResponse callProvider(VlRequest request) {
        String taskId = submit(request);
        return pollUntilDone(taskId);
    }

    /**
     * 将 PaddleOCR-VL sidecar 轮询响应解析为统一的 {@link OcrResult}。
     *
     * <p>提取 {@code text}、{@code confidence} 与 {@code blocks} 数组，逐个构造 {@link OcrBlock}（含行列表与包围盒）；
     * 元数据中记录 provider、任务 ID 与终态。
     *
     * @param response PaddleOCR-VL 响应（含任务 ID、终态、结果 JSON、耗时）
     * @return 统一的 OCR 识别结果（全文本、文本块列表、平均置信度、Provider 标识与耗时）
     */
    @Override
    protected OcrResult fromProviderResponse(VlResponse response) {
        VlSubmitEnvelope.VlPollEnvelope poll = response.poll();
        String text = poll != null && poll.text() != null ? poll.text() : "";
        float confidence = poll != null && poll.confidence() != null
                ? poll.confidence().floatValue() : 0.0f;

        List<OcrBlock> blocks = new ArrayList<>();
        List<VlSubmitEnvelope.Block> rawBlocks = poll != null ? poll.blocks() : null;
        if (rawBlocks != null) {
            for (VlSubmitEnvelope.Block rawBlock : rawBlocks) {
                blocks.add(parseBlock(rawBlock));
            }
        }

        Map<String, Object> metadata = Map.of(
                "provider", "paddle-vl",
                "task_id", response.taskId(),
                "state", response.state());
        return new OcrResult(text, blocks, confidence, metadata, response.latencyMs());
    }

    private String submit(VlRequest request) {
        // 单次方法内捕获一次 liveGrpcEndpoint()，避免在多个错误路径里重复计算同样的 URL；
        // 这仅是该次 submit() 调用内的局部变量 —— 不构成跨调用的缓存，hot-update 依然生效。
        String endpoint = liveGrpcEndpoint();
        String submitUrl = endpoint + "/submit";
        try {
            VlSubmitPayload payload = VlSubmitPayload.of(request, liveGpuPool());
            HttpResponse resp = httpClient
                    .post(submitUrl, payload)
                    .timeout(Duration.ofMillis(liveTimeoutMs()))
                    .execute();

            if (!resp.isSuccessful()) {
                throw new OcrException.SidecarUnavailable("paddle-vl", submitUrl,
                        new RuntimeException("submit HTTP " + resp.statusCode() + ": " + resp.bodyAsString()));
            }
            VlSubmitEnvelope submit = resp.bodyAs(VlSubmitEnvelope.class);
            String taskId = submit != null ? submit.taskId() : null;
            if (taskId == null || taskId.isBlank()) {
                throw new OcrException.SidecarUnavailable("paddle-vl", submitUrl,
                        new RuntimeException("submit response missing task_id: " + safeBody(resp.bodyAsString())));
            }
            return taskId;
        } catch (OcrException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OcrException.SidecarUnavailable("paddle-vl", submitUrl, e);
        }
    }

    private VlResponse pollUntilDone(String taskId) {
        // 同 submit()：单次轮询内捕获一次 endpoint / timeout，跨 hot-update 时下一次轮询会重新读。
        String pollUrl = liveGrpcEndpoint() + "/tasks/" + taskId;
        long deadline = System.currentTimeMillis() + liveTimeoutMs();
        long start = System.currentTimeMillis();
        while (true) {
            try {
                HttpResponse resp = httpClient
                        .get(pollUrl)
                        .timeout(Duration.ofMillis(POLL_INTERVAL_MS + 5_000))
                        .execute();

                if (!resp.isSuccessful()) {
                    throw new OcrException.SidecarUnavailable("paddle-vl", pollUrl,
                            new RuntimeException("poll HTTP " + resp.statusCode() + ": " + resp.bodyAsString()));
                }
                VlSubmitEnvelope.VlPollEnvelope polled = resp.bodyAs(VlSubmitEnvelope.VlPollEnvelope.class);
                String state = polled != null && polled.state() != null ? polled.state() : "UNKNOWN";

                if ("SUCCEEDED".equalsIgnoreCase(state)) {
                    long latency = System.currentTimeMillis() - start;
                    return new VlResponse(taskId, state, polled, latency);
                }
                if ("FAILED".equalsIgnoreCase(state)) {
                    String errCode = polled.errorCode() != null ? polled.errorCode() : "";
                    if ("VLM_OOM".equals(errCode)) {
                        int reqVram = polled.requiredVramMb() != null ? polled.requiredVramMb() : 0;
                        int availVram = polled.availableVramMb() != null ? polled.availableVramMb() : 0;
                        throw new OcrException.VlmOutOfMemory("paddle-vl", reqVram, availVram);
                    }
                    String errMsg = polled.errorMsg() != null ? polled.errorMsg() : "";
                    throw new OcrException.Unrecognized("paddle-vl",
                            "PaddleOCR-VL task FAILED: code=" + errCode + " msg=" + safeBody(errMsg));
                }
                if (System.currentTimeMillis() >= deadline) {
                    long elapsed = System.currentTimeMillis() - start;
                    throw new OcrException.VlmTimeout("paddle-vl", elapsed, liveTimeoutMs());
                }
                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
            } catch (OcrException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OcrException.ProviderUnavailable("paddle-vl", null, e);
            } catch (RuntimeException e) {
                throw new OcrException.SidecarUnavailable("paddle-vl", pollUrl, e);
            }
        }
    }

    private OcrBlock parseBlock(VlSubmitEnvelope.Block node) {
        String text = node.text() != null ? node.text() : "";
        float confidence = node.confidence() != null ? node.confidence().floatValue() : 0f;
        List<Point> box = parseBox(node.bbox());

        List<OcrLine> lines = new ArrayList<>();
        List<VlSubmitEnvelope.Line> rawLines = node.lines();
        if (rawLines != null) {
            for (VlSubmitEnvelope.Line line : rawLines) {
                String lineText = line.text() != null ? line.text() : "";
                float lineConf = line.confidence() != null ? line.confidence().floatValue() : 0f;
                lines.add(new OcrLine(lineText, parseBox(line.bbox()), lineConf));
            }
        }
        return new OcrBlock(text, box, confidence, lines);
    }

    private static List<Point> parseBox(List<List<Float>> box) {
        if (box == null) {
            return List.of();
        }
        List<Point> points = new ArrayList<>(box.size());
        for (List<Float> p : box) {
            if (p != null && p.size() >= 2) {
                points.add(new Point(
                        Math.round(p.get(0)),
                        Math.round(p.get(1))));
            }
        }
        return List.copyOf(points);
    }

    private static String mapLanguage(java.util.Set<Languages> langs) {
        if (langs == null || langs.isEmpty()) return "zh";
        Languages lang = langs.iterator().next();
        return switch (lang) {
            case CHINESE_SIMPLIFIED, CHINESE_SIMPLIFIED_AND_ENGLISH -> "zh";
            case ENGLISH, DIGITS_ONLY, LATIN -> "en";
            case CHINESE_TRADITIONAL -> "zh-Hant";
            case JAPANESE -> "ja";
            case KOREAN -> "ko";
            case ARABIC -> "ar";
            case RUSSIAN -> "ru";
            case HINDI -> "hi";
            case THAI -> "th";
            case VIETNAMESE -> "vi";
            default -> "en";
        };
    }

    private static String textOrDefault(String field, String defaultValue) {
        return defaultValue;
    }

    private static long longOrDefault(String field, long defaultValue) {
        return defaultValue;
    }

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

}
