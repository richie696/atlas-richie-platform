package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.obs.services.model.AccessControlList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.AclTypeEnum.*;

/**
 * 华为云 OBS ACL 类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0606.html">华为云 OBS ACL 文档</a>
 * @since 2025-01-XX
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "huawei_obs")
public class ObsAclTypeConverter implements AclTypeConverter<AccessControlList> {

    @Override
    public AccessControlList convertToEngineAcl(@Nonnull AclTypeEnum aclType) {
        return switch (aclType) {
            case PRIVATE -> AccessControlList.REST_CANNED_PRIVATE;
            case PUBLIC_READ -> AccessControlList.REST_CANNED_PUBLIC_READ;
            case PUBLIC_READ_WRITE -> AccessControlList.REST_CANNED_PUBLIC_READ_WRITE;
            case AUTHENTICATED_READ -> AccessControlList.REST_CANNED_AUTHENTICATED_READ;
            case BUCKET_OWNER_READ -> AccessControlList.REST_CANNED_BUCKET_OWNER_READ;
            case BUCKET_OWNER_FULL_CONTROL -> AccessControlList.REST_CANNED_BUCKET_OWNER_FULL_CONTROL;
            case LOG_DELIVERY_WRITE -> AccessControlList.REST_CANNED_LOG_DELIVERY_WRITE;
            default -> throw new IllegalArgumentException(
                    "不支持的 ACL 类型: %s. 仅支持: %s、%s、%s、%s、%s、%s和%s。".formatted(aclType,
                            PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE, AUTHENTICATED_READ,
                            BUCKET_OWNER_READ, BUCKET_OWNER_FULL_CONTROL, LOG_DELIVERY_WRITE));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.HUAWEI_OBS;
    }
}

