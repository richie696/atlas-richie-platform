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

