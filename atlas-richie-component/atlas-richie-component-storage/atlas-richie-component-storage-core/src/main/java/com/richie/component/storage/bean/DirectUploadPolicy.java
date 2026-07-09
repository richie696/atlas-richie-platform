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
import java.util.Map;

/**
 * 直传策略（用于客户端根据策略直接上传到对象存储）。
 * <p>
 * 字段语义约定（success × fallback 二维矩阵）：
 * <ul>
 *     <li>{@code success=true,  fallback=false}：主路径，使用对象存储 SDK 预签名的直传 URL/表单</li>
 *     <li>{@code success=true,  fallback=true} ：SDK 不可用时的兜底路径，由业务服务端代理中转</li>
 *     <li>{@code success=false, fallback=false}：生成失败（凭证缺失、配置错误等），仅含错误信息</li>
 *     <li>{@code success=false, fallback=true} ：SDK 生成失败已降级到兜底，兜底自身也失败</li>
 * </ul>
 * 调用方应根据 {@link #success} 决定是否使用本策略；{@link #fallback} 用于 UI 提示或埋点。
 */
@Data
@Builder(toBuilder = true)
public class DirectUploadPolicy implements Serializable {

    /**
     * 策略是否可用；为 {@code false} 时客户端应读取 {@link #errorMessage} 并放弃直传
     */
    private boolean success;

    /**
     * 错误信息（仅在 {@link #success} 为 {@code false} 时有值）
     */
    private String errorMessage;

    /**
     * HTTP 方法，通常是 PUT 或 POST
     */
    private String method;

    /**
     * 上传 URL
     */
    private String uploadUrl;

    /**
     * 上传 header（签名类或附加约束）
     */
    private Map<String, String> headers;

    /**
     * form 表单字段（用于 POST policy 场景）
     */
    private Map<String, String> formFields;

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
     * 是否兜底策略（非 SDK 预签名，由业务服务端代理中转）
     */
    private boolean fallback;
}

