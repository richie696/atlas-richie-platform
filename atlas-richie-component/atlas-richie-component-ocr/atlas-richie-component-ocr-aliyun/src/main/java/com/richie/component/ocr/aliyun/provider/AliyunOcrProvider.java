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
package com.richie.component.ocr.aliyun.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.aliyun.config.AliyunOcrProperties;
import com.richie.component.ocr.aliyun.protocol.AliyunOcrPayload;
import com.richie.component.ocr.aliyun.protocol.AliyunOcrResponse;
import com.richie.component.ocr.aliyun.protocol.AliyunRequest;
import com.richie.component.ocr.aliyun.protocol.AliyunResponse;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;

/**
 * 阿里云读光 OCR Provider 实现。
 *
 * <p>API 协议参考: 阿里云读光 OCR v1.0
 * <ul>
 *   <li>HTTP 端点: POST {endpoint}/v1/ocr/recognize</li>
 *   <li>鉴权: HTTP Header {@code Authorization: Bearer APPCODE ...}（阿里云 APPCODE 模式）</li>
 *   <li>请求体字段: {@code url}（公网可访问 URL）或 {@code body}（图片二进制 base64 字符串, 无 data: 前缀）</li>
 *   <li>响应: JSON, 顶层 {@code content} / {@code prism_wnumInfo} / {@code data} 等字段, 视具体能力而定</li>
 * </ul>
 *
 * <p>官方文档:
 * <a href="https://help.aliyun.com/zh/ocr/support/api-reference-for-alibaba-cloud-marketplace/" target="_blank">
 * 阿里云云市场 OCR API 总览</a> (APPCODE 鉴权)
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public class AliyunOcrProvider extends AbstractOcrProvider<AliyunRequest, AliyunResponse> {

    /** 默认阿里云读光 OCR HTTP JSON 服务端点。 */
    private static final String DEFAULT_ENDPOINT = "https://ocr-api.cn-shanghai.aliyuncs.com";
    /** 默认请求超时时间，单位毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    /** 默认 model 值。 */
    private static final String DEFAULT_MODEL = "standard-form";
    /** 调用阿里云 OCR HTTP 接口的共享客户端（不是 vendor 配置，不走 props）。 */
    private final HttpClient httpClient;
    /** 阿里云 OCR vendor 配置 Properties —— 每次调用 lazy 读取。 */
    private final AliyunOcrProperties props;

    /**
     * 构造基于共享 HTTP 客户端与 typed 配置的阿里云读光 OCR Provider。
     *
     * <p>仅保存 {@code props} 引用并 fast-fail 校验必填项
     * （{@code credentials.app-code}）；其他配置在每次调用时通过 {@code liveXxx()} 实时读取。
     *
     * @param props 阿里云 OCR 私有配置属性，由 {@code platform.component.ocr.aliyun.*} 绑定得到
     * @param httpClient 调用阿里云读光 OCR 端点的共享 HTTP 客户端，不能为 {@code null}
     * @throws OcrException.ConfigMissing 缺少 {@code credentials.app-code} 时抛出
     */
    public AliyunOcrProvider(AliyunOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.props = props;

        if (liveAppCode() == null) {
            throw new OcrException.ConfigMissing("aliyun", "credentials.app-code");
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

    private String liveAppCode() {
        return props.getCredentials() != null ? props.getCredentials().getAppCode() : null;
    }

    private String liveModel() {
        return props.getVendor() != null && props.getVendor().getModel() != null
                ? props.getVendor().getModel()
                : DEFAULT_MODEL;
    }

    private boolean liveFeature() {
        return props.getVendor() != null && props.getVendor().isFeature();
    }

    // --- AbstractOcrProvider 模板实现 ---

    @Override
    protected AliyunRequest toProviderRequest(OcrImage image, OcrOptions options) {
        // 把 4 种 OcrImage 变体统一翻译为"阿里云请求载荷"
        // 阿里云读光要求: url 字段（公网 URL）或 body 字段（裸 base64, 无 data: 前缀）
        if (image instanceof OcrImage.Bytes bytes) {
            // 字节流走 base64, 注意阿里云 body 字段不带 "data:" 前缀
            String base64 = Base64.getEncoder().encodeToString(bytes.data());
            return new AliyunRequest(null, base64, options);
        }
        if (image instanceof OcrImage.Url url) {
            // URL 模式: 直接传 url, auth 字段由业务侧保证 URL 已是预签名
            return new AliyunRequest(url.url(), null, options);
        }
        if (image instanceof OcrImage.Stream stream) {
            // Stream: 必须先物化（阿里云读光不支持流式上传）
            try (InputStream in = stream.input()) {
                byte[] data = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(data);
                return new AliyunRequest(null, base64, options);
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("aliyun", null, e);
            }
        }
        throw new IllegalArgumentException("Unsupported OcrImage variant: " + image.getClass().getName());
    }

    @Override
    protected AliyunResponse callProvider(AliyunRequest request) {
        try {
            String featureAttr = liveFeature() ? "advanced" : null;
            AliyunOcrPayload payload = request.imageUrl() != null
                    ? AliyunOcrPayload.ofUrl(request.imageUrl(), request.options(), liveModel(), featureAttr)
                    : AliyunOcrPayload.ofBase64(request.imageBase64(), request.options(), liveModel(), featureAttr);

            long start = System.currentTimeMillis();
            HttpResponse resp = httpClient.post(liveEndpoint() + "/v1/ocr/recognize", payload)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "APPCODE " + liveAppCode())
                    .timeout(Duration.ofMillis(liveTimeoutMs()))
                    .execute();
            long latencyMs = System.currentTimeMillis() - start;

            // 3. 状态码判断
            if (!resp.isSuccessful()) {
                throw new OcrException.ProviderUnavailable(
                        "aliyun", resp.statusCode(),
                        new RuntimeException(resp.statusCode() + " " + safeBody(resp.bodyAsString())));
            }

            // 4. 业务状态判断（阿里云 ret 数组）
            AliyunOcrResponse envelope = resp.bodyAs(AliyunOcrResponse.class);
            if (envelope == null) {
                throw new OcrException.ProviderUnavailable("aliyun", null,
                        new RuntimeException("Empty response body from aliyun"));
            }
            String ret = envelope.ret();
            if (ret != null && !ret.isBlank() && !"0".equals(ret) && !"200".equals(ret)) {
                throw new OcrException.Unrecognized("aliyun",
                        "aliyun ret=" + ret + " msg=" + safeBody(resp.bodyAsString()));
            }

            return new AliyunResponse(envelope, latencyMs);
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException.ProviderUnavailable("aliyun", null, e);
        }
    }

    @Override
    protected OcrResult fromProviderResponse(AliyunResponse response) {
        AliyunOcrResponse envelope = response.body();

        // 阿里云读光响应: 顶层 content（拼接文本）, prism_wordsInfo（词级定位）
        String text = envelope.content() != null ? envelope.content() : "";
        float confidence = envelope.prismWnum() != null
                ? (float) envelope.prismWnum().doubleValue() / 100f
                : 0.0f;

        List<OcrBlock> blocks = new ArrayList<>();
        List<AliyunOcrResponse.AliyunWordBlock> rawBlocks = envelope.prismWordsInfo();
        if (rawBlocks != null) {
            for (AliyunOcrResponse.AliyunWordBlock rawBlock : rawBlocks) {
                String blockText = rawBlock.word() != null ? rawBlock.word() : "";
                float blockConfidence = rawBlock.prob() != null
                        ? (float) rawBlock.prob().doubleValue() / 100f
                        : 0.0f;

                List<Point> box = parseBox(rawBlock.pos());
                List<OcrLine> lines = new ArrayList<>();
                List<AliyunOcrResponse.AliyunWordLine> rawLines = rawBlock.lines();
                if (rawLines != null) {
                    for (AliyunOcrResponse.AliyunWordLine rawLine : rawLines) {
                        String lineText = rawLine.word() != null ? rawLine.word() : "";
                        float lineConfidence = rawLine.prob() != null
                                ? (float) rawLine.prob().doubleValue() / 100f
                                : 0.0f;
                        lines.add(new OcrLine(lineText, parseBox(rawLine.pos()), lineConfidence));
                    }
                }
                blocks.add(new OcrBlock(blockText, box, blockConfidence, lines));
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        if (envelope.requestId() != null) {
            metadata.put("requestId", envelope.requestId());
        }
        metadata.put("provider", "aliyun");

        return new OcrResult(text, blocks, confidence, metadata, response.latencyMs());
    }

    private static List<Point> parseBox(List<List<Integer>> pos) {
        List<Point> box = new ArrayList<>(4);
        if (pos == null) {
            return box;
        }
        for (List<Integer> p : pos) {
            if (p != null && p.size() >= 2 && p.get(0) != null && p.get(1) != null) {
                box.add(new Point(p.get(0), p.get(1)));
            }
        }
        return box;
    }

    private static String safeBody(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
