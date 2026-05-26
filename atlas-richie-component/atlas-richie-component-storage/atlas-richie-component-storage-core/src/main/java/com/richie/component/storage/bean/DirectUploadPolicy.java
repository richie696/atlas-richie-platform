package com.richie.component.storage.bean;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 直传策略（用于客户端根据策略直接上传到对象存储）。
 */
@Data
@Builder(toBuilder = true)
public class DirectUploadPolicy implements Serializable {

    /**
     * 是否可用
     */
    private boolean success;

    /**
     * 错误信息
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
     * 是否兜底策略（非 SDK 预签名）
     */
    private boolean fallback;
}

