/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
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
package com.richie.component.storage.bean;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 直读策略（用于客户端根据策略直接访问私有对象）。
 * <p>
 * 与 {@link DirectUploadPolicy} 对称，由后端签发预签名下载 URL，
 * 前端/客户端使用此 URL 直接读取对象存储中的私有文件，无需经过业务服务器中转。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-18
 */
@Data
@Builder(toBuilder = true)
public class DirectDownloadPolicy implements Serializable {

    /**
     * 是否可用
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 预签名下载 URL（前端/客户端可直接通过 GET 访问）
     */
    private String downloadUrl;

    /**
     * 桶名称
     */
    private String bucketName;

    /**
     * 对象键
     */
    private String key;

    /**
     * 策略过期时间
     */
    private OffsetDateTime expireAt;

    /**
     * 是否兜底策略（非 SDK 预签名）
     */
    private boolean fallback;
}
