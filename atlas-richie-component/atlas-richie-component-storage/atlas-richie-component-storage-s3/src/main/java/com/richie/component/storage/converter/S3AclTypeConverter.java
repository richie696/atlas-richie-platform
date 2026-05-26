package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import static com.richie.component.storage.enums.AclTypeEnum.*;

/**
 * Amazon S3 ACL 类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/acl-overview.html">Amazon S3 ACL 概述</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aws_s3")
public class S3AclTypeConverter implements AclTypeConverter<ObjectCannedACL> {

    @Override
    public ObjectCannedACL convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        return switch (aclType) {
            case PRIVATE -> ObjectCannedACL.PRIVATE;
            case PUBLIC_READ -> ObjectCannedACL.PUBLIC_READ;
            case PUBLIC_READ_WRITE -> ObjectCannedACL.PUBLIC_READ_WRITE;
            case AUTHENTICATED_READ -> ObjectCannedACL.AUTHENTICATED_READ;
            case BUCKET_OWNER_READ -> ObjectCannedACL.BUCKET_OWNER_READ;
            case BUCKET_OWNER_FULL_CONTROL -> ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL;
            default -> throw new IllegalArgumentException(
                    "不支持的 ACL 类型: %s. 仅支持: %s、%s、%s、%s、%s和%s。".formatted(aclType,
                            PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                            BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.AWS_S3;
    }
}

