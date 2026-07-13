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
package com.richie.component.ocr.mineru.provider;

import com.richie.component.ocr.mineru.protocol.MineruPollEnvelope;
import com.richie.component.ocr.mineru.protocol.MineruRequest;
import com.richie.component.ocr.mineru.protocol.MineruResponse;
import com.richie.component.ocr.mineru.protocol.MineruUploadEnvelope;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;
import com.richie.component.ocr.mineru.config.MineruOcrProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MinerU Provider 实现。
 *
 * <p>MinerU 是 PDF 结构化识别服务，输出 Markdown / Docx。本 Provider 走异步 REST 协议
 * （假设中台自托管 MinerU 实例，真实场景部署在 GPU 节点）:
 * <ul>
 *   <li>{@code POST {endpoint}/upload} multipart/form-data {@code file=<pdf>} → 返回 {@code {"task_id": "..."}}</li>
 *   <li>{@code GET {endpoint}/tasks/{task_id}} → 返回 {@code {"state": "...", "markdown": "..."}}</li>
 * </ul>
 *
 * <p><b>同步阻塞</b>: PDF 识别慢, 同步 {@link #recognize} 内部轮询直至完成。
 *
 * <p><b>鉴权</b>: HTTP Header {@code Authorization: Bearer <api-key>}。
 *
 * <p><b>热更新友好 (L2)</b>: 所有 vendor 配置属性（endpoint / timeoutMs / apiKey）
 * 都不在构造期固化，改为在每次调用时按需通过 {@code liveXxx()} 方法读取 {@link #props}。
 * 此种模式下：
 * <ul>
 *   <li>Provider 不会"闭锁"配置 —— 业务侧通过 Spring Cloud {@code @RefreshScope} /
 *       Nacos Config Listener 替换 {@link MineruOcrProperties} Bean 实例后，下次调用即生效</li>
 *   <li>无运行时分支、无 reflection、无 thread-local</li>
 *   <li>校验分两段：构造期 fast-fail（仅 {@code api-key}）+ 每次调用 lazy re-read</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public class MineruOcrProvider extends AbstractOcrProvider<MineruRequest, MineruResponse> {

    /** 默认 MinerU 内部端点。 */
    private static final String DEFAULT_ENDPOINT = "http://mineru.internal:8000";
    /** 默认单次识别（涵盖上传 + 轮询直至完成）超时时间，单位毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 180_000L;
    /** 轮询间隔，单位毫秒。 */
    private static final long POLL_INTERVAL_MS = 3_000L;

    /** 调用 MinerU HTTP 接口的共享客户端（不是 vendor 配置，不走 props）。 */
    private final HttpClient httpClient;
    /** MinerU vendor 配置 Properties —— 每次调用 lazy 读取。 */
    private final MineruOcrProperties props;

    /**
     * 通过 typed {@link MineruOcrProperties} 构造 MinerU Provider。
     *
     * <p>仅保存 {@code props} 引用并 fast-fail 校验必填项
     * （{@code api-key}）；其他配置在每次调用时通过 {@code liveXxx()} 实时读取。
     *
     * @param props 从 {@code platform.component.ocr.mineru} 绑定的 typed 配置（含 endpoint / api-key / timeout-ms）
     * @param httpClient 共享的 HTTP 客户端，用于调用 MinerU 上传与轮询接口
     * @throws OcrException.ConfigMissing 当 {@code api-key} 未配置（null 或 blank）时抛出
     */
    public MineruOcrProvider(MineruOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.props = props;

        String apiKey = liveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new OcrException.ConfigMissing("mineru", "api-key");
        }
    }

    // --- live configuration accessors: 每次调用时实时读 props ---
    // 业务侧可以通过 Spring @RefreshScope / Nacos Config Listener 等替换 props Bean 引用实现热更新。

    private String liveEndpoint() {
        return props.getEndpoint() != null ? props.getEndpoint() : DEFAULT_ENDPOINT;
    }

    private long liveTimeoutMs() {
        return props.getTimeoutMs() > 0 ? props.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
    }

    private String liveApiKey() {
        return props.getApiKey();
    }

    // --- AbstractOcrProvider 模板实现 ---

    /**
     * 将统一的 {@link OcrImage} 物化为字节数组，并构造 MinerU 所需的 {@link MineruRequest}。
     *
     * <p>仅支持 {@link OcrImage.Bytes} 与 {@link OcrImage.Stream}（应为 PDF 字节流）；
     * {@link OcrImage.Url} 因 sidecar 不支持远程拉取而被拒绝。
     *
     * @param image 待识别 PDF（{@code Bytes} 或 {@code Stream}）
     * @param options 调用选项（MinerU 当前不解析该字段）
     * @return 包含 PDF 字节的 MinerU 请求对象
     * @throws OcrException.Unrecognized 当传入的 {@link OcrImage} 类型为 {@code Url} 时抛出
     * @throws OcrException.ProviderUnavailable 当读取 {@link OcrImage.Stream} 输入流发生 {@link IOException} 时抛出
     */
    @Override
    protected MineruRequest toProviderRequest(OcrImage image, OcrOptions options) {
        byte[] data;
        if (image instanceof OcrImage.Bytes b) {
            data = b.data();
        } else if (image instanceof OcrImage.Stream s) {
            try (InputStream in = s.input()) {
                data = in.readAllBytes();
            } catch (IOException e) {
                throw new OcrException.ProviderUnavailable("mineru", null, e);
            }
        } else {
            throw new OcrException.Unrecognized("mineru",
                    "MinerU only supports local Bytes/Stream PDF, not URL");
        }
        return new MineruRequest(data);
    }

    /**
     * 调用 MinerU 上传 PDF 后同步轮询直至完成，返回最终响应。
     *
     * @param request 已构造好的 MinerU 请求（PDF 字节）
     * @return 包含任务 ID、终态、Markdown 结果与耗时的 MinerU 响应
     * @throws OcrException.SidecarUnavailable 当 HTTP 5xx、连接失败或 IO 异常时抛出
     * @throws OcrException.VlmTimeout 当轮询超过 {@code timeoutMs} 仍未完成时抛出
     * @throws OcrException.Unrecognized 当任务终态为 {@code FAILED} 时抛出
     * @throws OcrException.ProviderUnavailable 当轮询被中断时抛出，并恢复线程中断状态
     */
    @Override
    protected MineruResponse callProvider(MineruRequest request) {
        String taskId = upload(request);
        return pollUntilDone(taskId);
    }

    /**
     * 将 MinerU 轮询响应（Markdown 文本）转换为统一的 {@link OcrResult}。
     *
     * <p>由于 MinerU 输出为结构化 Markdown，MinerU Provider 不解析 block 行级结构，全文即 Markdown；
     * 元数据中记录 provider、任务 ID 与输出格式。
     *
     * @param response MinerU 响应（含任务 ID、终态、Markdown 与耗时）
     * @return 统一的 OCR 识别结果（Markdown 全文、Provider 标识与耗时）
     */
    @Override
    protected OcrResult fromProviderResponse(MineruResponse response) {
        String markdown = response.markdown() != null ? response.markdown() : "";
        Map<String, Object> metadata = Map.of(
                "provider", "mineru",
                "task_id", response.taskId(),
                "format", "markdown");
        return new OcrResult(markdown, List.of(), 0.0f, metadata, response.latencyMs());
    }

    private String upload(MineruRequest request) {
        try {
            byte[] data = request.pdfData();

            HttpResponse resp = httpClient
                    .post(liveEndpoint() + "/upload")
                    .header("Authorization", "Bearer " + liveApiKey())
                    .multipart("file", "document.pdf", new ByteArrayInputStream(data))
                    .timeout(Duration.ofMillis(liveTimeoutMs()))
                    .execute();

            if (!resp.isSuccessful()) {
                throw new OcrException.SidecarUnavailable("mineru", liveEndpoint() + "/upload",
                        new RuntimeException("upload HTTP " + resp.statusCode() + ": " + resp.bodyAsString()));
            }
            MineruUploadEnvelope envelope = resp.bodyAs(MineruUploadEnvelope.class);
            String taskId = envelope != null ? envelope.taskId() : null;
            if (taskId == null || taskId.isBlank()) {
                throw new OcrException.SidecarUnavailable("mineru", liveEndpoint() + "/upload",
                        new RuntimeException("upload response missing task_id: " + safeBody(resp.bodyAsString())));
            }
            return taskId;
        } catch (OcrException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OcrException.SidecarUnavailable("mineru", liveEndpoint() + "/upload", e);
        }
    }

    private MineruResponse pollUntilDone(String taskId) {
        long deadline = System.currentTimeMillis() + liveTimeoutMs();
        long start = System.currentTimeMillis();
        while (true) {
            try {
                HttpResponse resp = httpClient
                        .get(liveEndpoint() + "/tasks/" + taskId)
                        .header("Authorization", "Bearer " + liveApiKey())
                        .timeout(Duration.ofMillis(POLL_INTERVAL_MS + 5_000))
                        .execute();

                if (!resp.isSuccessful()) {
                    throw new OcrException.SidecarUnavailable("mineru",
                            liveEndpoint() + "/tasks/" + taskId,
                            new RuntimeException("poll HTTP " + resp.statusCode() + ": " + safeBody(resp.bodyAsString())));
                }
                MineruPollEnvelope env = resp.bodyAs(MineruPollEnvelope.class);
                String state = env != null && env.state() != null ? env.state() : "UNKNOWN";

                if ("SUCCEEDED".equalsIgnoreCase(state)) {
                    long latency = System.currentTimeMillis() - start;
                    String markdown = env.markdown() != null ? env.markdown() : "";
                    return new MineruResponse(taskId, state, markdown, latency);
                }
                if ("FAILED".equalsIgnoreCase(state)) {
                    String errMsg = env.errorMsg() != null ? env.errorMsg() : "";
                    throw new OcrException.Unrecognized("mineru",
                            "MinerU task FAILED: " + safeBody(errMsg));
                }
                if (System.currentTimeMillis() >= deadline) {
                    long elapsed = System.currentTimeMillis() - start;
                    throw new OcrException.VlmTimeout("mineru", elapsed, liveTimeoutMs());
                }
                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
            } catch (OcrException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OcrException.ProviderUnavailable("mineru", null, e);
            } catch (RuntimeException e) {
                throw new OcrException.SidecarUnavailable("mineru",
                        liveEndpoint() + "/tasks/" + taskId, e);
            }
        }
    }

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
