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
package cn.richie696.component.storage.bean;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 对象探测结果。
 * <p>
 * {@code success=true, exists=false} 表示对象已确认不存在；{@code success=false} 表示探测失败，
 * 调用方不得将其视为对象不存在。
 */
@Data
@Builder
public class ObjectStatResponse implements Serializable {

    private boolean success;
    private boolean exists;

    private String errorCode;
    private String errorMessage;
    private String requestId;

    private String bucketName;
    private String key;
    private String versionId;

    private Long contentLength;
    private String contentType;
    private String contentEncoding;
    private OffsetDateTime lastModified;
    private String storageClass;

    /** ETag 是存储服务返回的对象标识，不能假定为 MD5。 */
    private String etag;

    /** 校验值，键为 {@code MD5}、{@code CRC64_ECMA}、{@code CRC32C} 或 {@code SHA256}。 */
    private Map<String, String> checksums;

    private Map<String, String> userMetadata;
}
