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
package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import jakarta.annotation.Nonnull;

/**
 * ACL 类型转换器接口
 * <p>
 * 将统一的 ACL 类型枚举转换为各存储引擎特定的 ACL 值
 * <p>
 * 使用泛型 {@code <T>} 支持不同存储引擎返回各自需要的 ACL 对象类型：
 * <ul>
 *   <li>华为云 OBS: 返回 {@code AccessControlList}</li>
 *   <li>阿里云 OSS: 返回 {@code CannedAccessControlList}</li>
 *   <li>腾讯云 COS: 返回 {@code CannedAccessControlList}</li>
 *   <li>AWS S3: 返回 {@code ObjectCannedACL}</li>
 *   <li>火山引擎 TOS: 返回 {@code ACLType}</li>
 *   <li>金山云 KS3: 返回 {@code CannedAccessControlList}</li>
 *   <li>MinIO: 返回 {@code String}</li>
 * </ul>
 *
 * @param <T> 存储引擎特定的 ACL 对象类型
 * @author richie696
 * @version 2.0
 * @since 2025-01-XX
 */
public interface AclTypeConverter<T> {

    /**
     * 将 ACL 类型转换为引擎特定的 ACL 对象
     *
     * @param aclType ACL 类型枚举
     * @return 返回对应的存储引擎 ACL 对象
     */
    T convertToEngineAcl(@Nonnull AclTypeEnum aclType);

    /**
     * 获取支持的存储引擎类型
     *
     * @return 支持的存储引擎枚举
     */
    StorageEngineEnum getSupportedEngine();

}

