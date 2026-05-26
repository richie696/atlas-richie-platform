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
