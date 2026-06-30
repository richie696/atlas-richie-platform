package com.richie.component.storage.converter;

import com.ksyun.ks3.service.common.StorageClass;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.StorageTypeEnum.*;

/**
 * 金山云KS3存储类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://docs.ksyun.com/documents/2353?type=3">金山云存储桶类型</a>
 * @since 2025-06-03 11:32:04
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "ksyun_ks3")
public class Ks3StorageTypeConverter implements StorageTypeConverter {

    @Override
    public String convertToEngineType(@Nonnull StorageTypeEnum storageType) {
        return switch (storageType) {
            case STANDARD -> StorageClass.Standard.toString();
            case STANDARD_IA -> StorageClass.StandardInfrequentAccess.toString();
            case ARCHIVE -> StorageClass.Archive.toString();
            default -> throw new StorageTypeUnsupportedException(getSupportedEngine(), storageType,
                    "仅支持: %s、%s和%s存储类型。".formatted(STANDARD, STANDARD_IA, ARCHIVE));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.VOLCENGINE_TOS;
    }
}
