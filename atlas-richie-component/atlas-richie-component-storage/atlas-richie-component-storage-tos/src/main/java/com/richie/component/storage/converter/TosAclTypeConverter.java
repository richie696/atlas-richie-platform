package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.volcengine.tos.comm.common.ACLType;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.AclTypeEnum.*;

/**
 * 火山引擎 TOS ACL 类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://www.volcengine.com/docs/6349/79929?lang=zh">火山引擎 TOS ACL 文档</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "volcengine_tos")
public class TosAclTypeConverter implements AclTypeConverter<ACLType> {

    @Override
    public ACLType convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        return switch (aclType) {
            case PRIVATE -> ACLType.ACL_PRIVATE;
            case PUBLIC_READ -> ACLType.ACL_PUBLIC_READ;
            case PUBLIC_READ_WRITE -> ACLType.ACL_PUBLIC_READ_WRITE;
            case AUTHENTICATED_READ -> ACLType.ACL_AUTHENTICATED_READ;
            case BUCKET_OWNER_READ -> ACLType.ACL_BUCKET_OWNER_READ;
            case BUCKET_OWNER_FULL_CONTROL -> ACLType.ACL_BUCKET_OWNER_FULL_CONTROL;
            case LOG_DELIVERY_WRITE -> ACLType.ACL_LOG_DELIVERY_WRITE;
            default -> throw new IllegalArgumentException(
                    "不支持的 ACL 类型: %s. 仅支持: %s、%s、%s、%s、%s、%s和%s。".formatted(aclType,
                            PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                            BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL, LOG_DELIVERY_WRITE));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.VOLCENGINE_TOS;
    }
}

