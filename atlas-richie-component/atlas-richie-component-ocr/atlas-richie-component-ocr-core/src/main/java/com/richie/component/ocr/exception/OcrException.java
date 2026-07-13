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
package com.richie.component.ocr.exception;

import java.util.List;

/**
 * OCR 异常体系 —— 仅承载底层真实错误（provider 失联 / 拒识 / VLM OOM 等）。
 *
 * <p>不承载编排层语义异常（限流被拒 / 熔断打开 / 自动 retry 用尽）—— 这些由编排层抛出,
 * 与本组件解耦。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public abstract sealed class OcrException extends RuntimeException
        permits OcrException.ConfigMissing,
                OcrException.ProviderUnavailable,
                OcrException.Unrecognized,
                OcrException.SidecarUnavailable,
                OcrException.VlmTimeout,
                OcrException.VlmOutOfMemory,
                OcrException.ImageTooLargeForSync,
                OcrException.NoAvailableProvider {

    private OcrException(String message) {
        super(message);
    }

    private OcrException(String message, Throwable cause) {
        super(message, cause);
    }

    // ---- 异常子类 ----

    /**
     * Provider 配置缺失（如未配 api-key-ref）。
     */
    public static final class ConfigMissing extends OcrException {
        private final String provider;
        private final String key;

        /**
         * @param provider Provider 名
         * @param key      缺失的配置项 key
         */
        public ConfigMissing(String provider, String key) {
            super("OCR provider[" + provider + "] missing config[" + key + "]");
            this.provider = provider;
            this.key = key;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return 缺失的配置项 key
         */
        public String key() { return key; }
    }

    /**
     * Provider 调用失败（HTTP 5xx / 网络超时 / gRPC 错误）。
     */
    public static final class ProviderUnavailable extends OcrException {
        private final String provider;
        private final Integer httpStatus;

        /**
         * @param provider   Provider 名
         * @param httpStatus HTTP 状态码（{@code null} 表示非 HTTP 错误, 如网络超时 / gRPC 不可用）
         * @param cause      底层异常
         */
        public ProviderUnavailable(String provider, Integer httpStatus, Throwable cause) {
            super(buildMessage(provider, httpStatus, cause), cause);
            this.provider = provider;
            this.httpStatus = httpStatus;
        }

        private static String buildMessage(String provider, Integer httpStatus, Throwable cause) {
            StringBuilder sb = new StringBuilder("OCR provider[").append(provider).append("] unavailable");
            if (httpStatus != null) {
                sb.append(": HTTP ").append(httpStatus);
            } else if (cause != null) {
                String msg = cause.getMessage();
                sb.append(": ").append(msg != null ? msg : cause.getClass().getSimpleName());
            }
            return sb.toString();
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return HTTP 状态码（{@code null} 表示非 HTTP 错误）
         */
        public Integer httpStatus() { return httpStatus; }
    }

    /**
     * Provider 拒识（图片格式不支持 / 图片损坏 / 内容安全审核拒绝）。
     */
    public static final class Unrecognized extends OcrException {
        private final String provider;
        private final String reason;

        /**
         * @param provider Provider 名
         * @param reason   拒识原因（业务侧直接展示给上游）
         */
        public Unrecognized(String provider, String reason) {
            super("OCR provider[" + provider + "] failed to recognize: " + reason);
            this.provider = provider;
            this.reason = reason;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return 拒识原因
         */
        public String reason() { return reason; }
    }

    /**
     * Sidecar 进程不可用（PID 退出 / 健康检查失败 / gRPC 连接拒绝）。
     */
    public static final class SidecarUnavailable extends OcrException {
        private final String provider;
        private final String endpoint;

        /**
         * @param provider Provider 名
         * @param endpoint Sidecar 地址（host:port）
         * @param cause    底层异常
         */
        public SidecarUnavailable(String provider, String endpoint, Throwable cause) {
            super("OCR sidecar[" + provider + " @ " + endpoint + "] unavailable", cause);
            this.provider = provider;
            this.endpoint = endpoint;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return Sidecar 地址
         */
        public String endpoint() { return endpoint; }
    }

    /**
     * VLM 单次识别超时（默认 30s；SELF_HOSTED_GPU Provider 在大图/低算力时常见）。
     */
    public static final class VlmTimeout extends OcrException {
        private final String provider;
        private final long elapsedMs;
        private final long budgetMs;

        /**
         * @param provider  Provider 名
         * @param elapsedMs 实际耗时（毫秒）
         * @param budgetMs  预算耗时（毫秒）
         */
        public VlmTimeout(String provider, long elapsedMs, long budgetMs) {
            super("OCR VLM[" + provider + "] timeout after " + elapsedMs + "ms (budget=" + budgetMs + ")");
            this.provider = provider;
            this.elapsedMs = elapsedMs;
            this.budgetMs = budgetMs;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return 实际耗时（毫秒）
         */
        public long elapsedMs() { return elapsedMs; }

        /**
         * @return 预算耗时（毫秒）
         */
        public long budgetMs() { return budgetMs; }
    }

    /**
     * VLM Provider 显存溢出（GPU OCR 推理时的 OOM；调用方应降级到 CPU Provider 或更小模型）。
     */
    public static final class VlmOutOfMemory extends OcrException {
        private final String provider;
        private final int requiredVramMb;
        private final int availableVramMb;

        /**
         * @param provider         Provider 名
         * @param requiredVramMb   所需显存（MB, 推断失败时实际请求量）
         * @param availableVramMb  当前可用显存（MB）
         */
        public VlmOutOfMemory(String provider, int requiredVramMb, int availableVramMb) {
            super("OCR VLM[" + provider + "] OOM: requires " + requiredVramMb + "MB, only " + availableVramMb + "MB available");
            this.provider = provider;
            this.requiredVramMb = requiredVramMb;
            this.availableVramMb = availableVramMb;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return 所需显存（MB）
         */
        public int requiredVramMb() { return requiredVramMb; }

        /**
         * @return 当前可用显存（MB）
         */
        public int availableVramMb() { return availableVramMb; }
    }

    /**
     * 图片大小超过同步接口阈值（Nacos 配置 max-sync-bytes，默认 4MB）。
     */
    public static final class ImageTooLargeForSync extends OcrException {
        private final String provider;
        private final long imageBytes;
        private final long thresholdBytes;

        /**
         * @param provider       Provider 名
         * @param imageBytes     图片实际字节数
         * @param thresholdBytes 同步接口阈值（字节）
         */
        public ImageTooLargeForSync(String provider, long imageBytes, long thresholdBytes) {
            super("OCR image(" + imageBytes + "B) exceeds sync threshold(" + thresholdBytes + "B) for provider[" + provider + "]");
            this.provider = provider;
            this.imageBytes = imageBytes;
            this.thresholdBytes = thresholdBytes;
        }

        /**
         * @return Provider 名
         */
        public String provider() { return provider; }

        /**
         * @return 图片实际字节数
         */
        public long imageBytes() { return imageBytes; }

        /**
         * @return 同步接口阈值（字节）
         */
        public long thresholdBytes() { return thresholdBytes; }
    }

    /**
     * 路由阶段无可用 Provider。
     */
    public static final class NoAvailableProvider extends OcrException {
        private final List<String> attempted;
        private final String context;

        /**
         * @param attempted 单个 Provider 名（被包装为单元素列表）
         * @param context   路由上下文（tenantId / GPU 可用性 等）
         */
        public NoAvailableProvider(String attempted, String context) {
            super("No available OCR provider; attempted=" + attempted + " context=" + context);
            this.attempted = List.of(attempted);
            this.context = context;
        }

        /**
         * @param attempted 所有尝试过的 Provider 名列表
         * @param context   路由上下文（tenantId / GPU 可用性 等）
         */
        public NoAvailableProvider(List<String> attempted, String context) {
            super("No available OCR provider; attempted=" + attempted + " context=" + context);
            this.attempted = List.copyOf(attempted);
            this.context = context;
        }

        /**
         * @return 所有尝试过的 Provider 名列表（不可变）
         */
        public List<String> attempted() { return attempted; }

        /**
         * @return 路由上下文（tenantId / GPU 可用性 等）
         */
        public String context() { return context; }
    }

}
