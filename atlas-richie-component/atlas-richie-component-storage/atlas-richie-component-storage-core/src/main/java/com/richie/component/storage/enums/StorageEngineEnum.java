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
package com.richie.component.storage.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

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
    MINIO("Minio", "minio", true),

    /**
     * 阿里云OSS
     */
    ALIYUN_OSS("阿里云OSS", "aliyun_oss", true),

    /**
     * 腾讯云COS
     */
    TENCENT_COS("腾讯云COS", "tencent_cos", true),

    /**
     * 华为云OBS
     */
    HUAWEI_OBS("华为云OBS", "huawei_obs", true),

    /**
     * AWS S3
     */
    AWS_S3("AWS S3", "aws_s3", true),

    /**
     * 金山云KS3
     */
    KSYUN_KS3("金山云KS3", "ksyun_ks3", true),

    /**
     * 火山引擎TOS
     */
    VOLCENGINE_TOS("火山引擎TOS", "volcengine_tos", true),

    /**
     * 微软Azure Blob
     */
    AZURE_BLOB("微软Azure Blob", "azure_blob", true),

    // ========== 文件协议引擎 ==========

    /**
     * FTP
     */
    FTP("FTP", "ftp", false),

    /**
     * SFTP
     */
    SFTP("SFTP", "sftp", false),

    /**
     * SMB
     */
    SMB("SMB", "smb", false),

    /**
     * 本地存储
     */
    LOCAL("本地存储", "local", false);

    private final String description;

    /**
     * 配置值，与 {@code @ConditionalOnProperty} 的 havingValue 对应
     */
    private final String configValue;

    /**
     * 是否为对象存储引擎（区别于文件协议引擎 FTP/SFTP/SMB/LOCAL）
     */
    private final boolean objectStorage;

    /**
     * 判断是否为对象存储引擎
     */
    public boolean isObjectStorage() {
        return objectStorage;
    }

    /**
     * 根据配置值查找对应的引擎枚举
     *
     * @param configValue 配置文件中的引擎值（如 "aws_s3"、"aliyun_oss"）
     * @return 对应的引擎枚举；未匹配或为 {@code null} 时返回 {@link Optional#empty()}
     */
    public static Optional<StorageEngineEnum> fromConfigValue(String configValue) {
        if (configValue == null) {
            return Optional.empty();
        }
        for (StorageEngineEnum engine : values()) {
            if (engine.configValue.equals(configValue)) {
                return Optional.of(engine);
            }
        }
        return Optional.empty();
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
