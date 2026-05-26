package com.richie.component.storage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对象存储引擎枚举
 *
 * @author richie696
 * @version 1.1
 * @since 2023-09-04 13:16:44
 */
@Getter
@RequiredArgsConstructor
public enum StorageEngineEnum {

    /**
     * Minio
     */
    MINIO("Minio", "minio"),

    /**
     * 阿里云OSS
     */
    ALIYUN_OSS("阿里云OSS", "aliyun_oss"),

    /**
     * 腾讯云COS
     */
    TENCENT_COS("腾讯云COS", "tencent_cos"),

    /**
     * 华为云OBS
     */
    HUAWEI_OBS("华为云OBS", "huawei_obs"),

    /**
     * AWS S3
     */
    AWS_S3("AWS S3", "aws_s3"),

    /**
     * 金山云KS3
     */
    KSYUN_KS3("金山云KS3", "ksyun_ks3"),

    /**
     * 火山引擎TOS
     */
    VOLCENGINE_TOS("火山引擎TOS", "volcengine_tos"),

    /**
     * 微软Azure Blob
     */
    AZURE_BLOB("微软Azure Blob", "azure_blob");

    private final String description;

    /**
     * 配置值，与 {@code @ConditionalOnProperty} 的 havingValue 对应
     */
    private final String configValue;

    /**
     * 根据配置值查找对应的引擎枚举
     *
     * @param configValue 配置文件中的引擎值（如 "aws_s3"、"aliyun_oss"）
     * @return 对应的引擎枚举，如果没有匹配则返回 null
     */
    public static StorageEngineEnum fromConfigValue(String configValue) {
        if (configValue == null) {
            return null;
        }
        for (StorageEngineEnum engine : values()) {
            if (engine.configValue.equals(configValue)) {
                return engine;
            }
        }
        return null;
    }

    /**
     * 获取所有有效的配置值，用于错误提示
     *
     * @return 所有有效配置值的逗号分隔字符串
     */
    public static String validConfigValues() {
        StringBuilder sb = new StringBuilder();
        StorageEngineEnum[] engines = values();
        for (int i = 0; i < engines.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(engines[i].configValue);
        }
        return sb.toString();
    }

}
