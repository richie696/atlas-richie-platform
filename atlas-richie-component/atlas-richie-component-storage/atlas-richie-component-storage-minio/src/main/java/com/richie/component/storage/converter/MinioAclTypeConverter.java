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
package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MinIO ACL 类型转换器
 * <p>
 * MinIO 兼容 S3 API，使用与 S3 相同的 ACL 值
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://min.io/docs/minio/linux/administration/object-management/object-access-control.html">MinIO ACL 文档</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "minio")
public class MinioAclTypeConverter implements AclTypeConverter<String> {

    @Override
    public String convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        // MinIO 兼容 S3 API，使用与 S3 相同的 ACL 值
        return switch (aclType) {
            case PRIVATE -> "private";
            case PUBLIC_READ -> "public-read";
            case PUBLIC_READ_WRITE -> "public-read-write";
            case AUTHENTICATED_READ -> "authenticated-read";
            case BUCKET_OWNER_READ -> "bucket-owner-read";
            case BUCKET_OWNER_FULL_CONTROL -> "bucket-owner-full-control";
            case LOG_DELIVERY_WRITE -> "log-delivery-write";
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.MINIO;
    }
}

