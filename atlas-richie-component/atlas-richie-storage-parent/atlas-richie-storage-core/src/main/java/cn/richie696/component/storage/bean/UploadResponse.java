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
 * 上传结果
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-06 13:19:58
 */
@Data
@Builder(toBuilder = true)
public class UploadResponse implements Serializable {

    /**
     * 执行结果
     */
    public boolean success;

    /**
     * 错误消息
     */
    public String errorMessage;

    /**
     * 请求ID
     */
    public String requestId;

    /**
     * 桶名称
     */
    public String bucketName;

    /**
     * 对象存储的键
     */
    private String key;

    /**
     * 文件版本号
     */
    private String versionId;

    /**
     * @deprecated 算法不明的值不可用于完整性校验；请改用 {@link #checksums}。
     */
    @Deprecated(since = "2026", forRemoval = false)
    private String hashValue;

    /** 云端返回的校验值，键为明确的算法名称。 */
    private Map<String, String> checksums;

    /**
     * 上传时间
     */
    private OffsetDateTime uploadTime;

    /**
     * 文件下载地址
     */
    private String url;

}
