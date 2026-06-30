package com.richie.component.storage.converter;

import com.obs.services.model.StorageClassEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.StorageTypeEnum.*;

/**
 * 华为云OBS存储类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0411.html">华为云存储桶类型</a>
 * @since 2025-06-03 11:32:04
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "huawei_obs")
public class ObsStorageTypeConverter implements StorageTypeConverter {

    @Override
    public String convertToEngineType(@Nonnull StorageTypeEnum storageType) {
        return switch (storageType) {
            case STANDARD -> StorageClassEnum.STANDARD.getCode();
            case STANDARD_IA -> StorageClassEnum.WARM.getCode();
            case ARCHIVE, COLD_ARCHIVE -> StorageClassEnum.COLD.getCode();
            case DEEP_COLD_ARCHIVE -> StorageClassEnum.DEEP_ARCHIVE.getCode();
            case INTELLIGENT_TIERING -> StorageClassEnum.INTELLIGENT_TIERING.getCode();
            default -> throw new StorageTypeUnsupportedException(getSupportedEngine(), storageType,
                    "仅支持: %s、%s、%s、%s、%s和%s存储类型。".formatted(STANDARD, STANDARD_IA,
                            ARCHIVE, COLD_ARCHIVE, DEEP_COLD_ARCHIVE, INTELLIGENT_TIERING));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.HUAWEI_OBS;
    }
}
