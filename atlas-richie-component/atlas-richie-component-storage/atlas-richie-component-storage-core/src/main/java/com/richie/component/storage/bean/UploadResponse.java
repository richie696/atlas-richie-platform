package com.richie.component.storage.bean;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

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
     * 云端计算的文件哈希值
     */
    private String hashValue;

    /**
     * 上传时间
     */
    private OffsetDateTime uploadTime;

    /**
     * 文件下载地址
     */
    private String url;

}
