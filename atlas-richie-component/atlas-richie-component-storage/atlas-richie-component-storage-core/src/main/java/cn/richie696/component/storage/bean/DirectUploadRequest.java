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

import java.util.Map;

/**
 * 申请对象存储直传策略的期望值。
 * <p>
 * 这些值来自客户端，上传完成后仍应以 {@link ObjectStatResponse} 返回的实际存储元数据为准。
 */
@Data
@Builder
public class DirectUploadRequest {

    private String key;
    private int expireSeconds;
    private Long contentLength;
    private String contentType;
    private Map<String, String> checksums;
    private Map<String, String> userMetadata;
}
