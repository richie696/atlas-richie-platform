package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import com.volcengine.tos.comm.common.StorageClassType;
import jakarta.annotation.Nonnull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.richie.component.storage.enums.StorageTypeEnum.*;

/**
 * 火山引擎TOS存储类型转换器
 *
 * @author richie696
 * @version 1.0
 * @see <a href="https://www.volcengine.com/docs/6349/104493">火山引擎存储桶类型</a>
 * @since 2025-06-03 11:32:04
 */
@Component
@ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "volcengine_tos")
public class TosStorageTypeConverter implements StorageTypeConverter {

    @Override
    public String convertToEngineType(@Nonnull StorageTypeEnum storageType) {
        return switch (storageType) {
            case STANDARD -> StorageClassType.STORAGE_CLASS_STANDARD.name();
            case STANDARD_IA -> StorageClassType.STORAGE_CLASS_IA.name();
            case ARCHIVE -> StorageClassType.STORAGE_CLASS_ARCHIVE.name();
            case ARCHIVE_FR -> StorageClassType.STORAGE_CLASS_ARCHIVE_FR.name();
            case COLD_ARCHIVE -> StorageClassType.STORAGE_CLASS_COLD_ARCHIVE.name();
            case DEEP_COLD_ARCHIVE -> StorageClassType.STORAGE_CLASS_DEEP_COLD_ARCHIVE.name();
            case INTELLIGENT_TIERING -> StorageClassType.STORAGE_CLASS_INTELLIGENT_TIERING.name();
            default -> throw new StorageTypeUnsupportedException(getSupportedEngine(), storageType,
                    "仅支持: %s、%s、%s、%s、%s、%s和%s存储类型。".formatted(STANDARD, STANDARD_IA,
                            ARCHIVE, ARCHIVE_FR, COLD_ARCHIVE, DEEP_COLD_ARCHIVE,
                            INTELLIGENT_TIERING));
        };
    }

    @Override
    public StorageEngineEnum getSupportedEngine() {
        return StorageEngineEnum.VOLCENGINE_TOS;
    }
}
