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
package com.richie.component.ocr.paddlevl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PaddleOCR-VL 私有配置属性（Phase B-2 交付，VLM 高精度路线）。
 *
 * <p>绑定前缀: {@code platform.component.ocr.paddle-vl}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: paddle-vl                      # 必填, 注意是 hyphen
 *       paddle-vl:
 *         provider-name: paddle-vl-prod
 *         grpc-endpoint: localhost:50051       # sidecar gRPC
 *         gpu-pool: 1
 *         timeout-ms: 120000                   # VLM 慢, 2 分钟
 * </pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.paddle-vl")
public class PaddleVlOcrProperties {

    /**
     * PaddleOCR-VL sidecar gRPC 服务地址（含 host:port），默认 {@code localhost:50051}。
     */
    private String grpcEndpoint = "localhost:50051";

    /**
     * GPU 并发槽位数量；VLM 显存占用较大，默认 1，建议与物理 GPU 卡数一致。
     */
    private int gpuPool = 1;

    /**
     * 单次识别的超时时间，单位毫秒；VLM 较慢，默认 120 秒。
     */
    private long timeoutMs = 120_000L;
}