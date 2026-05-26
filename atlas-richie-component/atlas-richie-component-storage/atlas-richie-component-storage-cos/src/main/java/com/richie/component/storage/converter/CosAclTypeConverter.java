package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.qcloud.cos.model.CannedAccessControlList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.AclTypeEnum.*;

/**
 * 腾讯云 COS ACL 类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://cloud.tencent.com/document/product/436/30752">腾讯云 COS ACL 文档</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "tencent_cos")
public class CosAclTypeConverter implements AclTypeConverter<CannedAccessControlList> {

    @Override
    public CannedAccessControlList convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        return switch (aclType) {
            case PRIVATE, AUTHENTICATED_READ, BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL -> CannedAccessControlList.Private;
            case PUBLIC_READ -> CannedAccessControlList.PublicRead;
            case PUBLIC_READ_WRITE -> CannedAccessControlList.PublicReadWrite;
            default -> throw new IllegalArgumentException(
                    "不支持的 ACL 类型: %s. 仅支持: %s、%s、%s、%s、%s和%s。".formatted(aclType,
                            PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                            BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.TENCENT_COS;
    }
}

