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

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 16:35:50
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.component.storage.object")
public class ObjectConfig {

    /**
     * 云存储引擎
     * @see StorageEngineEnum 云存储引擎枚举
     */
    private StorageEngineEnum engine;
    /**
     * 存储桶类型(默认：标准类型)
     */
    private StorageTypeEnum storageType = StorageTypeEnum.STANDARD;
    /**
     * 访问域名
     */
    private String endpoint;
    /**
     * 区域
     */
    private String region;
    /**
     * 访问KEY ID
     */
    private String accessKeyId;
    /**
     * 访问KEY密钥
     */
    private String accessKeySecret;
    /**
     * 桶名称
     */
    private String bucketName;
    /**
     * 桶内基础路径
     */
    private String basePath;
    /**
     * 是否在启动时自动创建存储桶（默认开启）。
     * <p>
     * 关闭时表示存储桶已由客户提供且凭证可能仅有某一前缀（{@link #basePath}）下的对象读写权限，
     * 此时将跳过 HeadBucket / CreateBucket，改为在该前缀下做一次临时对象的写入与读取校验；
     * 校验失败将抛出异常并阻断启动。
     */
    private boolean autoCreateBucket = true;
    /**
     * 对象访问控制列表（ACL）
     * <p>
     * 可选值：
     * <ul>
     *   <li>PRIVATE - 私有（默认，仅所有者可访问）</li>
     *   <li>PUBLIC_READ - 公共读（所有人可读，仅所有者可写）</li>
     *   <li>PUBLIC_READ_WRITE - 公共读写（所有人可读写）</li>
     *   <li>AUTHENTICATED_READ - 认证用户读</li>
     *   <li>BUCKET_OWNER_READ - 桶所有者读</li>
     *   <li>BUCKET_OWNER_FULL_CONTROL - 桶所有者完全控制</li>
     *   <li>LOG_DELIVERY_WRITE - 日志传递写</li>
     * </ul>
     * <p>
     * 注意：不同存储引擎可能支持不同的 ACL 值，通过 AclTypeConverter 转换为引擎特定的值。
     * 如果未设置，则使用默认值（通常为 PRIVATE）。
     */
    private AclTypeEnum acl = AclTypeEnum.PUBLIC_READ;

}
