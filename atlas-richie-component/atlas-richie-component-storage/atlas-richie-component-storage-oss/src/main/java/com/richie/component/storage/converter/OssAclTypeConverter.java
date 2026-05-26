package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.aliyun.oss.model.CannedAccessControlList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.AclTypeEnum.*;

/**
 * 阿里云 OSS ACL 类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://help.aliyun.com/zh/oss/user-guide/object-acl">阿里云 OSS ACL 文档</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aliyun_oss")
public class OssAclTypeConverter implements AclTypeConverter<CannedAccessControlList> {

    @Override
    public CannedAccessControlList convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        return switch (aclType) {
            case PRIVATE -> CannedAccessControlList.Private;
            case PUBLIC_READ -> CannedAccessControlList.PublicRead;
            case PUBLIC_READ_WRITE -> CannedAccessControlList.PublicReadWrite;
            case AUTHENTICATED_READ, BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL -> CannedAccessControlList.AuthenticatedRead;
            default -> throw new IllegalArgumentException(
                    "不支持的 ACL 类型: %s. 仅支持: %s、%s、%s、%s、%s和%s。".formatted(aclType,
                            PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                            BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.ALIYUN_OSS;
    }
}

