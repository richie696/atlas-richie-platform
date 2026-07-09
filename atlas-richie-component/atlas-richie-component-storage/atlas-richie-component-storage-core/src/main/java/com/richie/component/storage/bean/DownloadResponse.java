/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 下载数据
 *
 * @author richie696
 * @version 1.0
 * @since 2023-10-16 17:52:04
 * @param <T> 下载数据类型
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Accessors(chain = true)
public class DownloadResponse<T> {


    /**
     * 执行结果
     */
    private boolean success;
    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 请求ID
     */
    @Builder.Default
    private String requestId = "";

    /**
     * 桶名称
     */
    @Builder.Default
    private String bucketName = "";

    /**
     * 文件版本号
     */
    @Builder.Default
    private String versionId = "";

    /**
     * 对象存储的键
     */
    private String key;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件MD5
     */
    private String contentMD5;

    /**
     * 文件编码
     */
    private String contentEncoding;

    /**
     * 文件内容
     * <p style="color:red">（注：请勿下载过大的文件，否则可能导致JVM堆外内存溢出）
     */
    private T data;

}
